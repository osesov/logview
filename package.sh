rm -rf target bundle
mvn clean package

for type in app-image; do
    rm -rf JsonlViewer
    CMD=(
        jpackage
            --input target
            --name JsonlViewer
            --main-jar jsonl-viewer-javafx-1.0-SNAPSHOT-jar-with-dependencies.jar
            --main-class app.MainApp
            --type $type
            # --add-modules javafx.controls
            --java-options "--add-modules=javafx.controls"
            --app-version 1.0.0
            --vendor "Oleg Sesov"
            -d bundle
    )

    "${CMD[@]}"
done
