package net.simplyrin.artifactupdater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.simplyrin.rinstream.RinStream;

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
public class Main {

	public static void main(String[] args) {
		new RinStream().enableError();
		new Main().run(args);
	}

	private boolean async;

	public void run(String[] args) {
		List<File> files = new ArrayList<>();
		for (int i = 0; args.length > i; i++) {
			String value = args[i];
			File file = new File(value);
			if (file.exists()) {
				files.add(file);
				System.out.println("[ArtifactUpdater] " + file.getName() + " を読み込みました。");
			} else {
				if (value.equalsIgnoreCase("-mode=async")) {
					this.async = true;
				}
			}
		}

		System.out.println("[ArtifactUpdater] 実行モード: " + (this.async ? "非" : "") + "同期");

		for (File file : files) {
			Runnable task = () -> {
				JenkinsManager manager = new JenkinsManager(file);
				manager.start();
			};
			if (this.async) {
				new Thread(task).start();
			} else {
				task.run();
			}
		}
	}

}
