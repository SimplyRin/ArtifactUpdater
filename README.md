# ArtifactUpdater
Jenkins を使用しているサイトから更新があれば自動的にダウンロードしてくるもの

[BungeeRunner](https://github.com/SimplyRin/BungeeRunner) の代わりになるものです。

ArfifactUpdater フォルダ内に設定ファイルのサンプルを置いておきます。

# 実行
サーバーを実行する前に走らせて下さい

複数のファイルを同時に実行させたい場合は起動引数に `-mode=async` と入力して下さい。

## BungeeCord

```
java -jar ArtifactUpdater.jar AtfifactUpdater/bungeecord.json
java -jar BungeeCord.jar
```

## Spigot

```
java -jar ArtifactUpdater.jar -mode=async AtfifactUpdater/viaversion.json AtfifactUpdater/viabackwards.json AtfifactUpdater/viarewind.json
java -jar Spigot.jar
```
