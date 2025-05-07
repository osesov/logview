# README

Quick and somewhat dirty project to view JSONL file.

## Debug under vscode

To prepare classpath use

```shell
mvn dependency:copy-dependencies
```


Currently `mvn compile` populates `target/lib` as well, which can be used for vscode/command line execution.

## install

mvn package produces installable package in `target/jsonl-viewer-<VERSION>.zip`
