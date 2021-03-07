package net.simplyrin.artifactupdater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Created by SimplyRin on 2021/03/07.
 *
 * Copyright (c) 2021 SimplyRin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class JenkinsManager {

	private File file;
	private File buildNumber;

	private JsonObject jsonObject;
	private JsonObject jenkins;

	private String name;
	private String userAgent;
	private String buildNumberUrl;
	private String artifactUrl;

	public JenkinsManager(File file) {
		this.file = file;

		String value = this.readFile(this.file);

		this.jsonObject = JsonParser.parseString(value).getAsJsonObject();

		this.name = this.getString(this.jsonObject, "Name");
		this.userAgent = this.getString(this.jsonObject, "User-Agent");
	}

	public String getPrefix() {
		return "[" + this.name + "] ";
	}

	public void start() {
		this.jenkins = this.jsonObject.get("Jenkins").getAsJsonObject();

		this.buildNumberUrl = this.getString(this.jenkins, "BuildNumber");

		this.buildNumber = new File(this.getString(this.jsonObject, "NumberFile"));
		this.buildNumber.getParentFile().mkdirs();
		if (this.buildNumber.exists()) {
			this.artifactUrl = this.getString(this.jenkins, "Artifact");

			String number = this.getBuildNumber();
			String dlNumber = this.getDownloadedBuildNumber();

			if (dlNumber == null) {
				System.err.println(this.getPrefix() + "ビルド番号の取得に失敗しました。");
				this.buildNumber.delete();
			} else if (!number.equals(dlNumber)) {
				System.out.println(this.getPrefix() + "アップデート (現在: v" + dlNumber + ", 最新: v" + number + ") を確認しました。");
				System.out.println(this.getPrefix() + "成果物をダウンロードしています...。");

				File artifact = this.downloadArtifact();
				File extract = this.unzip(artifact);

				JsonObject move = this.jsonObject.get("Move").getAsJsonObject();
				String pathStr = this.getString(move, "Path");
				File path = new File(pathStr);
				boolean enableWildcardSearch = pathStr.contains("*");

				List<File> list = new ArrayList<>();
				if (enableWildcardSearch) {
					for (File file : path.getParentFile().listFiles((FileFilter) new WildcardFileFilter(path.getName()))) {
						list.add(file);
					}
				} else if (path.exists()) {
					list.add(path);
				}

				File target = new File(this.getString(move, "Target"));
				for (File file : list) {
					File targetFile = new File(target, file.getName());

					int i = 1;
					while (true) {
						if (targetFile.exists()) {
							String base = FilenameUtils.getBaseName(file.getName());
							String extension = FilenameUtils.getExtension(file.getName());
							targetFile = new File(target, base + " (" + i + ")" + extension);
						} else {
							break;
						}
					}
					targetFile.getParentFile().mkdirs();

					try {
						Files.move(Paths.get(file.getAbsolutePath()), Paths.get(targetFile.getAbsolutePath()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				// 成果物を指定フォルダに移動
				JsonObject save = this.jsonObject.get("Save").getAsJsonObject();
				String atrifactPath = this.getString(save, "ArtifactPath");

				File artifacts = extract;
				for (String folder : atrifactPath.split("/")) {
					artifacts = new File(artifacts, folder);
				}

				System.out.println(this.getPrefix() + "成果物ファイルパス: " + artifacts.getPath());

				// 成果物が複数ある場合など
				List<File> saveArtifactList = new ArrayList<>();
				String saveTarget = this.getString(save, "SaveTarget");
				enableWildcardSearch = saveTarget.contains("*");
				if (enableWildcardSearch) {
					for (File file : artifacts.listFiles((FileFilter) new WildcardFileFilter(saveTarget))) {
						saveArtifactList.add(file);
					}
				} else {
					saveArtifactList.add(new File(artifacts, saveTarget));
				}

				for (File file : saveArtifactList) {
					System.out.println(this.getPrefix() + "- " + file.getName());
					File targetFolder = new File(this.getString(save, "Folder"));
					File targetFile = new File(targetFolder, file.getName());

					try {
						Files.copy(Paths.get(file.getAbsolutePath()), Paths.get(targetFile.getAbsolutePath()));
						System.out.println(this.getPrefix() + "成果物を " + targetFile.getPath() + " に移動しました。");
						this.updateDownloadedBuildNumber();
					} catch (IOException e) {
						System.err.println(this.getPrefix() + "成果物を " + targetFile.getPath() + " に移動できませんでした。");
						e.printStackTrace();
					}
				}
			} else {
				System.out.println(this.getPrefix() + "最新の " + this.name + " を使用しています。");
			}
		} else {
			System.out.println(this.getPrefix() + "ビルドの番号ファイルを作成しています...。");
			try {
				this.buildNumber.createNewFile();

				String number = this.getBuildNumber();
				FileWriter fileWriter = new FileWriter(this.buildNumber);
				fileWriter.write(number);
				fileWriter.close();
				System.out.println(this.getPrefix() + "ビルドの番号ファイルを作成しました。");
			} catch (Exception e) {
				System.err.println(this.getPrefix() + "ビルドの番号ファイルの作成に失敗しました。");
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return 最新のビルド番号を取得し返します
	 */
	public String getBuildNumber() {
		try {
			HttpsURLConnection connection = (HttpsURLConnection) new URL(this.buildNumberUrl).openConnection();
			connection.addRequestProperty("user-agent", this.userAgent);
			connection.connect();

			Scanner scanner = new Scanner(connection.getInputStream());
			String buildNumber = scanner.nextLine();
			scanner.close();
			return buildNumber;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return 現在ダウンロード済みのビルド番号を返します
	 */
	public String getDownloadedBuildNumber() {
		try {
			FileReader fileReader = new FileReader(this.buildNumber);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String buildNumber = bufferedReader.readLine();
			bufferedReader.close();
			return buildNumber;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return 更新できたかどうか
	 *
	 */
	public boolean updateDownloadedBuildNumber() {
		try {
			FileWriter fileWriter = new FileWriter(this.buildNumber);
			fileWriter.write(this.getBuildNumber());
			fileWriter.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * @return Json で指定した Artifact URL のファイルをダウンロードしてくる
	 */
	public File downloadArtifact() {
		try {
			File folder = new File(this.getString(this.jsonObject, "TempFolder"));
			folder.mkdirs();

			File downloadedFile = new File(folder, "artifact-" + this.name
					+ "-" + UUID.randomUUID().toString().split("-")[0] + ".zip");

			HttpsURLConnection connection = (HttpsURLConnection) new URL(this.artifactUrl).openConnection();
			connection.addRequestProperty("user-agent", this.userAgent);
			connection.connect();

			FileUtils.copyInputStreamToFile(connection.getInputStream(), downloadedFile);
			return downloadedFile;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param ZIP ファイル
	 * @return ZIP ファイルを展開して展開フォルダーを返す
	 *
	 * https://stackoverflow.com/questions/30335332/apache-commons-unzip-method
	 */
	public File unzip(File file) {
		String fileBaseName = FilenameUtils.getBaseName(file.getName());
		Path destFolderPath = Paths.get(file.getParentFile().getPath(), fileBaseName);

		try {
			ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ, StandardCharsets.UTF_8);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				Path entryPath = destFolderPath.resolve(entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				} else {
					Files.createDirectories(entryPath.getParent());
					try (InputStream in = zipFile.getInputStream(entry)) {
						try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
							IOUtils.copy(in, out);
						}
					}
				}
			}
			zipFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return destFolderPath.toFile();
	}

	/**
	 * @param file
	 * @return 指定したファイルの内容を全行読み込み返す物
	 *
	 * https://qiita.com/penguinshunya/items/353bb1c555f337b0cf6d
	 */
	public String readFile(File file) {
		try {
			return Files.lines(Paths.get(file.getPath()), Charset.forName("UTF-8"))
					.collect(Collectors.joining(System.getProperty("line.separator")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param JsonObject
	 * @param Path
	 * @return JsonObject から指定したパスの値を取得して返す
	 */
	public String getString(JsonObject jsonObject, String path) {
		return jsonObject.get(path).getAsString();
	}

}
