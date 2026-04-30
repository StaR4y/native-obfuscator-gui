package by.radioegor146.gui;

import by.radioegor146.NativeObfuscator;
import by.radioegor146.gui.build.NativeBuildConfig;
import by.radioegor146.gui.build.NativeBuildPipeline;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NativeObfuscatorGui extends Application {

    private static final Object OUTPUT_LOCK = new Object();
    private static final String VERSION = "3.5.4r";

    private final TextField inputJarField = new TextField();
    private final TextField outputDirField = new TextField();
    private final TextField librariesDirField = new TextField();
    private final TextField blackListField = new TextField();
    private final TextField whiteListField = new TextField();
    private final TextField plainLibraryNameField = new TextField();
    private final TextField customLibraryDirField = new TextField();
    private final TextField toolsDirField = new TextField(defaultToolsDirectory().toString());
    private final CheckBox annotationsCheckBox = new CheckBox();
    private final CheckBox debugJarCheckBox = new CheckBox();
    private final CheckBox compileNativeCheckBox = new CheckBox();
    private final CheckBox embedNativeCheckBox = new CheckBox();
    private final CheckBox autoDownloadToolsCheckBox = new CheckBox();
    private final ComboBox<String> buildTypeComboBox = new ComboBox<>();
    private final ToggleGroup platformGroup = new ToggleGroup();
    private final List<Button> configurationButtons = new ArrayList<>();
    private final TextArea logArea = new TextArea();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label();
    private final Label toastLabel = new Label();
    private final Button runButton = new Button();
    private final Button openOutputButton = new Button();
    private final Button clearLogButton = new Button();
    private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(3.5));

    private TabPane detailsTabPane;
    private UiLanguage language = UiLanguage.EN;
    private Task<Void> activeTask;
    private String stylesheet;
    private String statusKey = "status.waiting";

    {
        embedNativeCheckBox.setSelected(true);
        autoDownloadToolsCheckBox.setSelected(true);
        buildTypeComboBox.getItems().addAll("Release", "Debug");
        buildTypeComboBox.setValue("Release");
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stylesheet = getClass().getResource("native-obfuscator.css").toExternalForm();

        Scene scene = new Scene(createRoot(stage), 1220, 780);
        scene.getStylesheets().add(stylesheet);

        stage.setTitle("Native Obfuscator");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    private StackPane createRoot(Stage stage) {
        configurationButtons.clear();
        updateStaticTexts();

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("app-shell");
        layout.setTop(createHeader(stage));
        layout.setCenter(createWorkspace(stage));
        layout.setBottom(createStatusBar());

        configureToast();

        StackPane root = new StackPane(layout, toastLabel);
        root.getStyleClass().add("app-shell");
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toastLabel, new Insets(0, 0, 58, 0));
        installDropSupport(root);

        setRunning(isRunning());
        return root;
    }

    private HBox createHeader(Stage stage) {
        HBox header = new HBox(16);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Region mark = new Region();
        mark.getStyleClass().add("brand-mark");

        VBox titleBox = new VBox(2);
        Label title = new Label("Native Obfuscator");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label(t("app.subtitle") + " " + VERSION);
        subtitle.getStyleClass().add("muted");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label languageLabel = new Label(t("language"));
        languageLabel.getStyleClass().add("toolbar-label");
        ComboBox<UiLanguage> languageBox = new ComboBox<>();
        languageBox.getStyleClass().add("language-box");
        languageBox.getItems().addAll(UiLanguage.EN);
        languageBox.setValue(language);
        languageBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue != language) {
                language = newValue;
                rebuild(stage);
            }
        });

        addStyleClass(runButton, "primary-button");
        runButton.setDefaultButton(true);
        runButton.setOnAction(event -> startObfuscation(stage));
        openOutputButton.setOnAction(event -> openOutputDirectory());

        header.getChildren().addAll(mark, titleBox, spacer, languageLabel, languageBox, openOutputButton, runButton);
        return header;
    }

    private SplitPane createWorkspace(Stage stage) {
        SplitPane workspace = new SplitPane(createProjectPane(stage), createDetailsPane(stage));
        workspace.getStyleClass().add("workspace");
        workspace.setDividerPositions(0.38);
        return workspace;
    }

    private ScrollPane createProjectPane(Stage stage) {
        VBox content = new VBox(16);
        content.getStyleClass().add("project-pane");
        content.getChildren().addAll(
                section(t("section.project"), inputOutputGrid(stage)),
                section(t("section.execution"), executionGrid())
        );

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("project-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setMinWidth(360);
        scrollPane.setPrefWidth(430);
        return scrollPane;
    }

    private TabPane createDetailsPane(Stage stage) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("details-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                tab(t("tab.filters"), section(t("section.scope"), filterGrid(stage))),
                tab(t("tab.native"), section(t("section.nativeOptions"), nativeOptionsGrid(stage))),
                tab(t("tab.console"), createConsolePane())
        );
        detailsTabPane = tabPane;
        return tabPane;
    }

    private Tab tab(String title, Region content) {
        Tab tab = new Tab(title);
        VBox wrapper = new VBox(content);
        wrapper.getStyleClass().add("tab-content");
        VBox.setVgrow(content, Priority.ALWAYS);
        tab.setContent(wrapper);
        return tab;
    }

    private VBox section(String titleText, Region body) {
        VBox section = new VBox(14);
        section.getStyleClass().add("section");
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        section.getChildren().addAll(title, body);
        return section;
    }

    private GridPane inputOutputGrid(Stage stage) {
        GridPane grid = baseGrid();
        addPathRow(grid, 0, t("field.inputJar"), inputJarField,
                button(t("button.choose"), t("tip.chooseJar"), () -> chooseJar(stage, inputJarField)));
        addPathRow(grid, 1, t("field.outputDir"), outputDirField,
                button(t("button.choose"), t("tip.chooseOutput"), () -> chooseDirectory(stage, outputDirField)));
        addPathRow(grid, 2, t("field.libraries"), librariesDirField,
                button(t("button.choose"), t("tip.chooseLibraries"), () -> chooseDirectory(stage, librariesDirField)));

        inputJarField.setPromptText("D:\\\\app\\\\target.jar");
        outputDirField.setPromptText("D:\\\\app\\\\native-output");
        librariesDirField.setPromptText(t("prompt.optional"));
        return grid;
    }

    private GridPane executionGrid() {
        GridPane grid = baseGrid();
        Object selectedPlatform = platformGroup.getSelectedToggle() == null
                ? by.radioegor146.Platform.HOTSPOT
                : platformGroup.getSelectedToggle().getUserData();
        platformGroup.getToggles().clear();

        HBox platformButtons = new HBox(8);
        platformButtons.getStyleClass().add("segmented");
        platformButtons.getChildren().addAll(
                platformButton("HotSpot", by.radioegor146.Platform.HOTSPOT),
                platformButton("Standard", by.radioegor146.Platform.STD_JAVA),
                platformButton("Android", by.radioegor146.Platform.ANDROID)
        );
        for (javafx.scene.control.Toggle toggle : platformGroup.getToggles()) {
            if (selectedPlatform == toggle.getUserData()) {
                platformGroup.selectToggle(toggle);
                break;
            }
        }
        if (platformGroup.getSelectedToggle() == null && !platformGroup.getToggles().isEmpty()) {
            platformGroup.selectToggle(platformGroup.getToggles().get(0));
        }

        HBox flags = new HBox(18, annotationsCheckBox, debugJarCheckBox);
        flags.setAlignment(Pos.CENTER_LEFT);

        addNodeRow(grid, 0, t("field.platform"), platformButtons);
        addNodeRow(grid, 1, t("field.flags"), flags);
        return grid;
    }

    private GridPane filterGrid(Stage stage) {
        GridPane grid = baseGrid();
        addPathRow(grid, 0, t("field.blackList"), blackListField,
                button(t("button.choose"), t("tip.chooseBlackList"), () -> chooseTextFile(stage, blackListField)));
        addPathRow(grid, 1, t("field.whiteList"), whiteListField,
                button(t("button.choose"), t("tip.chooseWhiteList"), () -> chooseTextFile(stage, whiteListField)));

        blackListField.setPromptText(t("prompt.optional"));
        whiteListField.setPromptText(t("prompt.optional"));
        return grid;
    }

    private GridPane nativeOptionsGrid(Stage stage) {
        GridPane grid = baseGrid();
        addPathRow(grid, 0, t("field.plainLib"), plainLibraryNameField, clearButton(plainLibraryNameField));
        addPathRow(grid, 1, t("field.nativeDir"), customLibraryDirField, clearButton(customLibraryDirField));
        addPathRow(grid, 2, t("field.toolsDir"), toolsDirField,
                button(t("button.choose"), t("tip.chooseToolsDir"), () -> chooseDirectory(stage, toolsDirField)));

        HBox nativeFlags = new HBox(18, compileNativeCheckBox, embedNativeCheckBox, autoDownloadToolsCheckBox);
        nativeFlags.setAlignment(Pos.CENTER_LEFT);
        addNodeRow(grid, 3, t("field.nativeBuild"), nativeFlags);
        addNodeRow(grid, 4, t("field.buildType"), buildTypeComboBox);

        plainLibraryNameField.setPromptText(t("prompt.plainLib"));
        customLibraryDirField.setPromptText(t("prompt.nativeDir"));
        toolsDirField.setPromptText(defaultToolsDirectory().toString());
        return grid;
    }

    private VBox createConsolePane() {
        VBox pane = new VBox(12);
        pane.getStyleClass().add("console-pane");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        clearLogButton.setOnAction(event -> logArea.clear());
        actions.getChildren().add(clearLogButton);

        addStyleClass(logArea, "console");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        pane.getChildren().addAll(actions, logArea);
        return pane;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label caption = new Label(t("status.caption"));
        caption.getStyleClass().add("status-caption");
        addStyleClass(statusLabel, "status-label");
        addStyleClass(progressBar, "run-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        bar.getChildren().addAll(caption, statusLabel, progressBar);
        return bar;
    }

    private GridPane baseGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(104);
        labelColumn.setPrefWidth(116);

        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
        return grid;
    }

    private void addPathRow(GridPane grid, int row, String labelText, TextField field, Button action) {
        addStyleClass(field, "path-field");
        HBox box = new HBox(8, field, action);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        addNodeRow(grid, row, labelText, box);
    }

    private void addNodeRow(GridPane grid, int row, String labelText, Region node) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        grid.add(label, 0, row);
        grid.add(node, 1, row);
    }

    private Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        configurationButtons.add(button);
        return button;
    }

    private Button clearButton(TextField field) {
        return button(t("button.clear"), t("tip.clear"), field::clear);
    }

    private ToggleButton platformButton(String text, by.radioegor146.Platform platform) {
        ToggleButton button = new ToggleButton(text);
        button.setUserData(platform);
        button.setToggleGroup(platformGroup);
        button.getStyleClass().add("segment-button");
        return button;
    }

    private void updateStaticTexts() {
        annotationsCheckBox.setText(t("checkbox.annotations"));
        annotationsCheckBox.setTooltip(new Tooltip(t("tip.annotations")));
        debugJarCheckBox.setText(t("checkbox.debugJar"));
        debugJarCheckBox.setTooltip(new Tooltip(t("tip.debugJar")));
        compileNativeCheckBox.setText(t("checkbox.compileNative"));
        compileNativeCheckBox.setTooltip(new Tooltip(t("tip.compileNative")));
        embedNativeCheckBox.setText(t("checkbox.embedNative"));
        embedNativeCheckBox.setTooltip(new Tooltip(t("tip.embedNative")));
        autoDownloadToolsCheckBox.setText(t("checkbox.autoDownloadTools"));
        autoDownloadToolsCheckBox.setTooltip(new Tooltip(t("tip.autoDownloadTools")));
        runButton.setText(t("button.run"));
        runButton.setTooltip(new Tooltip(t("tip.run")));
        openOutputButton.setText(t("button.openOutput"));
        openOutputButton.setTooltip(new Tooltip(t("tip.openOutput")));
        clearLogButton.setText(t("button.clearLog"));
        logArea.setPromptText(t("prompt.log"));
        if (!statusLabel.textProperty().isBound()) {
            statusLabel.setText(t(statusKey));
        }
    }

    private void addStyleClass(javafx.scene.Node node, String styleClass) {
        if (!node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    private void rebuild(Stage stage) {
        Scene scene = stage.getScene();
        if (scene != null) {
            detachReusableControls();
            scene.setRoot(createRoot(stage));
        }
    }

    private void detachReusableControls() {
        detach(inputJarField);
        detach(outputDirField);
        detach(librariesDirField);
        detach(blackListField);
        detach(whiteListField);
        detach(plainLibraryNameField);
        detach(customLibraryDirField);
        detach(toolsDirField);
        detach(annotationsCheckBox);
        detach(debugJarCheckBox);
        detach(compileNativeCheckBox);
        detach(embedNativeCheckBox);
        detach(autoDownloadToolsCheckBox);
        detach(buildTypeComboBox);
        detach(logArea);
        detach(progressBar);
        detach(statusLabel);
        detach(toastLabel);
        detach(runButton);
        detach(openOutputButton);
        detach(clearLogButton);
    }

    private void detach(javafx.scene.Node node) {
        if (node.getParent() instanceof javafx.scene.layout.Pane) {
            ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
        }
    }

    private void configureToast() {
        addStyleClass(toastLabel, "toast");
        toastLabel.setManaged(false);
        toastLabel.setMouseTransparent(true);
        toastLabel.setVisible(false);
    }

    private void chooseJar(Stage stage, TextField target) {
        FileChooser chooser = fileChooser(t("dialog.chooseJar"), target);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("filter.jar"), "*.jar"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            target.setText(file.getAbsolutePath());
            if (isEmpty(outputDirField.getText())) {
                File parent = file.getParentFile();
                if (parent != null) {
                    outputDirField.setText(new File(parent, "native-output").getAbsolutePath());
                }
            }
        }
    }

    private void chooseTextFile(Stage stage, TextField target) {
        FileChooser chooser = fileChooser(t("dialog.chooseText"), target);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(t("filter.text"), "*.txt", "*.list"),
                new FileChooser.ExtensionFilter(t("filter.all"), "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            target.setText(file.getAbsolutePath());
        }
    }

    private FileChooser fileChooser(String title, TextField target) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        Path path = pathFromText(target.getText());
        if (path != null) {
            File initial = Files.isDirectory(path) ? path.toFile() : path.getParent() == null ? null : path.getParent().toFile();
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
        }
        return chooser;
    }

    private void chooseDirectory(Stage stage, TextField target) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("dialog.chooseDirectory"));
        Path path = pathFromText(target.getText());
        if (path != null) {
            File initial = Files.isDirectory(path) ? path.toFile() : path.getParent() == null ? null : path.getParent().toFile();
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
        }
        File directory = chooser.showDialog(stage);
        if (directory != null) {
            target.setText(directory.getAbsolutePath());
        }
    }

    private void installDropSupport(javafx.scene.Node root) {
        root.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        root.setOnDragDropped(event -> {
            boolean completed = false;
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                applyDroppedFiles(dragboard.getFiles());
                completed = true;
            }
            event.setDropCompleted(completed);
            event.consume();
        });
    }

    private void applyDroppedFiles(List<File> files) {
        for (File file : files) {
            String lowerName = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && lowerName.endsWith(".jar")) {
                inputJarField.setText(file.getAbsolutePath());
                continue;
            }
            if (file.isFile() && (lowerName.endsWith(".txt") || lowerName.endsWith(".list"))) {
                if (isEmpty(blackListField.getText())) {
                    blackListField.setText(file.getAbsolutePath());
                } else {
                    whiteListField.setText(file.getAbsolutePath());
                }
                continue;
            }
            if (file.isDirectory()) {
                if (isEmpty(outputDirField.getText())) {
                    outputDirField.setText(file.getAbsolutePath());
                } else {
                    librariesDirField.setText(file.getAbsolutePath());
                }
            }
        }
    }

    private void startObfuscation(Stage stage) {
        if (isRunning()) {
            return;
        }

        final RunConfig config;
        try {
            config = readConfig();
        } catch (IllegalArgumentException ex) {
            setStatus("status.configError");
            appendLog(t("log.configError") + ": " + ex.getMessage());
            showToast(false);
            showAlert(stage, t("alert.configError"), ex.getMessage(), Alert.AlertType.WARNING);
            return;
        }

        logArea.clear();
        selectConsoleTab();
        setStatus("status.preparing");
        progressBar.setProgress(-1);

        activeTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(t("status.prepareOutput"));
                Files.createDirectories(config.outputDir);

                updateMessage(t("status.scanLibraries"));
                List<Path> libraries = findLibraries(config.librariesDir);
                appendLog(t("log.libraryCount") + ": " + libraries.size());

                updateMessage(t("status.readRules"));
                List<String> blackList = readLines(config.blackListFile, Collections.<String>emptyList());
                List<String> whiteList = config.whiteListFile == null ? null : readLines(config.whiteListFile, Collections.<String>emptyList());

                updateMessage(t("status.processing"));
                runWithCapturedOutput(new ThrowingRunnable() {
                    @Override
                    public void run() throws Exception {
                        NativeObfuscator obfuscator = new NativeObfuscator();
                        obfuscator.process(
                                config.inputJar,
                                config.outputDir,
                                libraries,
                                blackList,
                                whiteList,
                                config.plainLibraryName,
                                config.customLibraryDirectory,
                                config.platform,
                                config.useAnnotations,
                                config.generateDebugJar
                        );

                        if (config.compileNative) {
                            updateMessage(t("status.nativeBuild"));
                            Path outputJar = config.outputDir.resolve(config.inputJar.getFileName());
                            NativeBuildConfig nativeBuildConfig = new NativeBuildConfig(
                                    config.outputDir.resolve("cpp"),
                                    outputJar,
                                    config.toolsDirectory,
                                    config.platform,
                                    obfuscator.getNativeDir(),
                                    config.plainLibraryName,
                                    config.buildType,
                                    config.autoDownloadTools,
                                    config.embedNative
                            );
                            new NativeBuildPipeline().run(nativeBuildConfig);
                        }
                    }
                });

                updateMessage(t("status.completed"));
                return null;
            }
        };

        statusLabel.textProperty().bind(activeTask.messageProperty());
        setRunning(true);

        activeTask.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            setStatus("status.completed");
            progressBar.setProgress(1);
            setRunning(false);
            appendLog(t("log.completed") + ": " + config.outputDir);
            showToast(true);
        });

        activeTask.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            setStatus("status.failed");
            progressBar.setProgress(0);
            setRunning(false);
            Throwable error = activeTask.getException();
            appendLog(stackTrace(error));
            showToast(false);
            showAlert(stage, t("alert.failed"), error == null ? t("error.unknown") : error.getMessage(), Alert.AlertType.ERROR);
        });

        Thread worker = new Thread(activeTask, "native-obfuscator-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void selectConsoleTab() {
        if (detailsTabPane != null && !detailsTabPane.getTabs().isEmpty()) {
            detailsTabPane.getSelectionModel().select(detailsTabPane.getTabs().size() - 1);
        }
    }

    private RunConfig readConfig() {
        Path inputJar = requiredFile(inputJarField, t("error.inputRequired"));
        String inputName = inputJar.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!inputName.endsWith(".jar")) {
            throw new IllegalArgumentException(t("error.inputJar"));
        }

        Path outputDir = requiredPath(outputDirField, t("error.outputRequired"));
        if (Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException(t("error.outputNotDirectory"));
        }
        if (inputJar.getParent() != null && inputJar.getParent().equals(outputDir)) {
            throw new IllegalArgumentException(t("error.outputSameDirectory"));
        }

        Path librariesDir = optionalDirectory(librariesDirField, t("error.librariesMissing"));
        Path blackListFile = optionalFile(blackListField, t("error.blackListMissing"));
        Path whiteListFile = optionalFile(whiteListField, t("error.whiteListMissing"));

        ToggleButton selectedPlatform = (ToggleButton) platformGroup.getSelectedToggle();
        if (selectedPlatform == null) {
            throw new IllegalArgumentException(t("error.platformRequired"));
        }

        RunConfig config = new RunConfig();
        config.inputJar = inputJar;
        config.outputDir = outputDir;
        config.librariesDir = librariesDir;
        config.blackListFile = blackListFile;
        config.whiteListFile = whiteListFile;
        config.plainLibraryName = optionalText(plainLibraryNameField);
        config.customLibraryDirectory = optionalText(customLibraryDirField);
        config.toolsDirectory = optionalPath(toolsDirField, defaultToolsDirectory()).toAbsolutePath().normalize();
        config.buildType = buildTypeComboBox.getValue() == null ? "Release" : buildTypeComboBox.getValue();
        config.compileNative = compileNativeCheckBox.isSelected();
        config.embedNative = embedNativeCheckBox.isSelected();
        config.autoDownloadTools = autoDownloadToolsCheckBox.isSelected();
        config.platform = (by.radioegor146.Platform) selectedPlatform.getUserData();
        config.useAnnotations = annotationsCheckBox.isSelected();
        config.generateDebugJar = debugJarCheckBox.isSelected();
        return config;
    }

    private Path requiredFile(TextField field, String message) {
        Path path = requiredPath(field, message);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(message);
        }
        return path;
    }

    private Path requiredPath(TextField field, String message) {
        Path path = pathFromText(field.getText());
        if (path == null) {
            throw new IllegalArgumentException(message);
        }
        return path.toAbsolutePath().normalize();
    }

    private Path optionalFile(TextField field, String missingMessage) {
        Path path = pathFromText(field.getText());
        if (path == null) {
            return null;
        }
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(missingMessage + ": " + path);
        }
        return path;
    }

    private Path optionalDirectory(TextField field, String missingMessage) {
        Path path = pathFromText(field.getText());
        if (path == null) {
            return null;
        }
        path = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(missingMessage + ": " + path);
        }
        return path;
    }

    private Path pathFromText(String value) {
        if (isEmpty(value)) {
            return null;
        }
        return Paths.get(value.trim());
    }

    private Path optionalPath(TextField field, Path fallback) {
        Path path = pathFromText(field.getText());
        return path == null ? fallback : path;
    }

    private String optionalText(TextField field) {
        return isEmpty(field.getText()) ? null : field.getText().trim();
    }

    private List<Path> findLibraries(Path directory) throws IOException {
        if (directory == null) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(directory, FileVisitOption.FOLLOW_LINKS)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String value = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return value.endsWith(".jar") || value.endsWith(".zip");
                    })
                    .collect(Collectors.toList());
        }
    }

    private List<String> readLines(Path file, List<String> fallback) throws IOException {
        if (file == null) {
            return fallback;
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private void runWithCapturedOutput(ThrowingRunnable runnable) throws Exception {
        synchronized (OUTPUT_LOCK) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            UiLogOutputStream uiOut = new UiLogOutputStream();
            PrintStream uiPrint = new PrintStream(uiOut, true, StandardCharsets.UTF_8.name());
            PrintStream teeOut = new PrintStream(new TeeOutputStream(originalOut, uiPrint), true, StandardCharsets.UTF_8.name());
            PrintStream teeErr = new PrintStream(new TeeOutputStream(originalErr, uiPrint), true, StandardCharsets.UTF_8.name());

            try {
                System.setOut(teeOut);
                System.setErr(teeErr);
                runnable.run();
            } finally {
                teeOut.flush();
                teeErr.flush();
                uiPrint.flush();
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }

    private void openOutputDirectory() {
        Path outputDir = pathFromText(outputDirField.getText());
        if (outputDir == null) {
            appendLog(t("log.outputNotSet"));
            return;
        }
        try {
            Files.createDirectories(outputDir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(outputDir.toFile());
            } else {
                appendLog(t("log.desktopUnsupported") + ": " + outputDir);
            }
        } catch (IOException ex) {
            appendLog(t("log.openOutputFailed") + ": " + ex.getMessage());
        }
    }

    private void setRunning(boolean running) {
        runButton.setDisable(running);
        openOutputButton.setDisable(running);
        clearLogButton.setDisable(running);
        inputJarField.setDisable(running);
        outputDirField.setDisable(running);
        librariesDirField.setDisable(running);
        blackListField.setDisable(running);
        whiteListField.setDisable(running);
        plainLibraryNameField.setDisable(running);
        customLibraryDirField.setDisable(running);
        toolsDirField.setDisable(running);
        annotationsCheckBox.setDisable(running);
        debugJarCheckBox.setDisable(running);
        compileNativeCheckBox.setDisable(running);
        embedNativeCheckBox.setDisable(running);
        autoDownloadToolsCheckBox.setDisable(running);
        buildTypeComboBox.setDisable(running);
        platformGroup.getToggles().forEach(toggle -> ((ToggleButton) toggle).setDisable(running));
        configurationButtons.forEach(button -> button.setDisable(running));
    }

    private boolean isRunning() {
        return activeTask != null && activeTask.isRunning();
    }

    private void setStatus(String key) {
        statusKey = key;
        if (!statusLabel.textProperty().isBound()) {
            statusLabel.setText(t(key));
        }
    }

    private void showAlert(Stage stage, String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(stylesheet);
        alert.showAndWait();
    }

    private void showToast(boolean success) {
        toastTimer.stop();
        toastLabel.setText(success ? "Conversion successful" : "Conversion Failed");
        toastLabel.getStyleClass().removeAll("toast-success", "toast-failed");
        addStyleClass(toastLabel, success ? "toast-success" : "toast-failed");
        toastLabel.setVisible(true);
        toastTimer.setOnFinished(event -> toastLabel.setVisible(false));
        toastTimer.playFromStart();
    }

    private void appendLog(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Runnable append = () -> {
            logArea.appendText(text);
            if (!text.endsWith("\n")) {
                logArea.appendText(System.lineSeparator());
            }
        };
        if (javafx.application.Platform.isFxApplicationThread()) {
            append.run();
        } else {
            javafx.application.Platform.runLater(append);
        }
    }

    private String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return t("error.unknown");
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Path defaultToolsDirectory() {
        return Paths.get(System.getProperty("user.home"), ".native-obfuscator", "tools");
    }

    private String t(String key) {
        return en(key);
    }

    private String zh(String key) {
        switch (key) {
            case "app.subtitle": return "可视化工作台";
            case "language": return "语言";
            case "section.project": return "项目";
            case "section.execution": return "执行";
            case "section.scope": return "处理范围";
            case "section.nativeOptions": return "Native 选项";
            case "tab.filters": return "过滤";
            case "tab.native": return "Native";
            case "tab.console": return "控制台";
            case "field.inputJar": return "输入 JAR";
            case "field.outputDir": return "输出目录";
            case "field.libraries": return "依赖库";
            case "field.platform": return "目标平台";
            case "field.flags": return "开关";
            case "field.blackList": return "黑名单";
            case "field.whiteList": return "白名单";
            case "field.plainLib": return "Plain Lib";
            case "field.nativeDir": return "Native 目录";
            case "field.toolsDir": return "工具目录";
            case "field.nativeBuild": return "Native 构建";
            case "field.buildType": return "构建类型";
            case "checkbox.annotations": return "启用注解";
            case "checkbox.debugJar": return "生成 debug.jar";
            case "checkbox.compileNative": return "自动编译";
            case "checkbox.embedNative": return "嵌入 JAR";
            case "checkbox.autoDownloadTools": return "自动下载工具";
            case "button.choose": return "选择";
            case "button.clear": return "清除";
            case "button.run": return "开始转换";
            case "button.openOutput": return "打开输出";
            case "button.clearLog": return "清空日志";
            case "prompt.optional": return "可选";
            case "prompt.plainLib": return "可选，例如 native_library";
            case "prompt.nativeDir": return "可选，例如 native0";
            case "prompt.log": return "运行日志";
            case "tip.chooseJar": return "选择需要转换的 .jar 文件";
            case "tip.chooseOutput": return "选择生成结果目录";
            case "tip.chooseLibraries": return "选择依赖库目录";
            case "tip.chooseBlackList": return "选择 blacklist 文本文件";
            case "tip.chooseWhiteList": return "选择 whitelist 文本文件";
            case "tip.chooseToolsDir": return "选择本地构建工具目录";
            case "tip.clear": return "清空此项";
            case "tip.annotations": return "读取 @Native / @NotNative 注解";
            case "tip.debugJar": return "同时输出非可执行的 debug.jar";
            case "tip.compileNative": return "生成 C++ 后调用 CMake 编译动态库";
            case "tip.embedNative": return "将动态库写入输出 JAR 的 native 目录";
            case "tip.autoDownloadTools": return "缺少 CMake/Ninja/Windows 工具链时下载到本地目录";
            case "tip.run": return "在后台执行转换任务";
            case "tip.openOutput": return "打开输出目录";
            case "dialog.chooseJar": return "选择输入 JAR";
            case "dialog.chooseText": return "选择文本文件";
            case "dialog.chooseDirectory": return "选择目录";
            case "filter.jar": return "JAR 文件";
            case "filter.text": return "文本文件";
            case "filter.all": return "所有文件";
            case "status.caption": return "状态";
            case "status.waiting": return "等待配置";
            case "status.configError": return "配置错误";
            case "status.preparing": return "准备运行";
            case "status.prepareOutput": return "准备输出目录";
            case "status.scanLibraries": return "扫描依赖库";
            case "status.readRules": return "读取过滤规则";
            case "status.processing": return "转换中";
            case "status.nativeBuild": return "编译 Native 库";
            case "status.completed": return "完成";
            case "status.failed": return "失败";
            case "log.configError": return "配置错误";
            case "log.libraryCount": return "依赖库数量";
            case "log.completed": return "完成";
            case "log.outputNotSet": return "输出目录未设置";
            case "log.desktopUnsupported": return "当前环境不支持打开文件管理器";
            case "log.openOutputFailed": return "无法打开输出目录";
            case "alert.configError": return "配置错误";
            case "alert.failed": return "转换失败";
            case "error.inputRequired": return "请选择输入 JAR";
            case "error.inputJar": return "输入文件必须是 .jar";
            case "error.outputRequired": return "请选择输出目录";
            case "error.outputNotDirectory": return "输出路径不是目录";
            case "error.outputSameDirectory": return "输出目录不能与输入 JAR 所在目录相同";
            case "error.librariesMissing": return "依赖库目录不存在";
            case "error.blackListMissing": return "黑名单文件不存在";
            case "error.whiteListMissing": return "白名单文件不存在";
            case "error.platformRequired": return "请选择目标平台";
            case "error.unknown": return "未知错误";
            default: return key;
        }
    }

    private String en(String key) {
        switch (key) {
            case "app.subtitle": return "Visual workbench";
            case "language": return "Language";
            case "section.project": return "Project";
            case "section.execution": return "Execution";
            case "section.scope": return "Processing Scope";
            case "section.nativeOptions": return "Native Options";
            case "tab.filters": return "Filters";
            case "tab.native": return "Native";
            case "tab.console": return "Console";
            case "field.inputJar": return "Input JAR";
            case "field.outputDir": return "Output";
            case "field.libraries": return "Libraries";
            case "field.platform": return "Platform";
            case "field.flags": return "Flags";
            case "field.blackList": return "Blacklist";
            case "field.whiteList": return "Whitelist";
            case "field.plainLib": return "Plain Lib";
            case "field.nativeDir": return "Native Dir";
            case "field.toolsDir": return "Tools Dir";
            case "field.nativeBuild": return "Native Build";
            case "field.buildType": return "Build Type";
            case "checkbox.annotations": return "Use annotations";
            case "checkbox.debugJar": return "Create debug.jar";
            case "checkbox.compileNative": return "Auto compile";
            case "checkbox.embedNative": return "Embed in JAR";
            case "checkbox.autoDownloadTools": return "Auto download tools";
            case "button.choose": return "Browse";
            case "button.clear": return "Clear";
            case "button.run": return "Run";
            case "button.openOutput": return "Open Output";
            case "button.clearLog": return "Clear Log";
            case "prompt.optional": return "Optional";
            case "prompt.plainLib": return "Optional, e.g. native_library";
            case "prompt.nativeDir": return "Optional, e.g. native0";
            case "prompt.log": return "Run log";
            case "tip.chooseJar": return "Choose the .jar file to convert";
            case "tip.chooseOutput": return "Choose the output directory";
            case "tip.chooseLibraries": return "Choose the libraries directory";
            case "tip.chooseBlackList": return "Choose the blacklist text file";
            case "tip.chooseWhiteList": return "Choose the whitelist text file";
            case "tip.chooseToolsDir": return "Choose the local build tools directory";
            case "tip.clear": return "Clear this value";
            case "tip.annotations": return "Read @Native / @NotNative annotations";
            case "tip.debugJar": return "Also output a non-executable debug.jar";
            case "tip.compileNative": return "Compile the generated C++ sources with CMake";
            case "tip.embedNative": return "Write the native library into the output JAR native directory";
            case "tip.autoDownloadTools": return "Download missing CMake/Ninja/Windows toolchain into the local tools directory";
            case "tip.run": return "Run conversion in the background";
            case "tip.openOutput": return "Open the output directory";
            case "dialog.chooseJar": return "Choose Input JAR";
            case "dialog.chooseText": return "Choose Text File";
            case "dialog.chooseDirectory": return "Choose Directory";
            case "filter.jar": return "JAR files";
            case "filter.text": return "Text files";
            case "filter.all": return "All files";
            case "status.caption": return "Status";
            case "status.waiting": return "Waiting";
            case "status.configError": return "Config error";
            case "status.preparing": return "Preparing";
            case "status.prepareOutput": return "Preparing output";
            case "status.scanLibraries": return "Scanning libraries";
            case "status.readRules": return "Reading rules";
            case "status.processing": return "Processing";
            case "status.nativeBuild": return "Building native library";
            case "status.completed": return "Completed";
            case "status.failed": return "Failed";
            case "log.configError": return "Config error";
            case "log.libraryCount": return "Libraries";
            case "log.completed": return "Completed";
            case "log.outputNotSet": return "Output directory is not set";
            case "log.desktopUnsupported": return "Desktop file manager is not supported";
            case "log.openOutputFailed": return "Failed to open output directory";
            case "alert.configError": return "Config Error";
            case "alert.failed": return "Conversion Failed";
            case "error.inputRequired": return "Choose an input JAR";
            case "error.inputJar": return "Input file must be a .jar";
            case "error.outputRequired": return "Choose an output directory";
            case "error.outputNotDirectory": return "Output path is not a directory";
            case "error.outputSameDirectory": return "Output directory cannot match the input JAR directory";
            case "error.librariesMissing": return "Libraries directory does not exist";
            case "error.blackListMissing": return "Blacklist file does not exist";
            case "error.whiteListMissing": return "Whitelist file does not exist";
            case "error.platformRequired": return "Choose a target platform";
            case "error.unknown": return "Unknown error";
            default: return key;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private enum UiLanguage {
        EN("English");

        private final String label;

        UiLanguage(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class RunConfig {
        private Path inputJar;
        private Path outputDir;
        private Path librariesDir;
        private Path blackListFile;
        private Path whiteListFile;
        private Path toolsDirectory;
        private String plainLibraryName;
        private String customLibraryDirectory;
        private String buildType;
        private by.radioegor146.Platform platform;
        private boolean useAnnotations;
        private boolean generateDebugJar;
        private boolean compileNative;
        private boolean embedNative;
        private boolean autoDownloadTools;
    }

    private final class UiLogOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public synchronized void write(int value) {
            if (value == '\n') {
                flushBuffer();
                return;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }

        @Override
        public synchronized void flush() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.size() == 0) {
                return;
            }
            String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            buffer.reset();
            appendLog(line);
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int value) throws IOException {
            first.write(value);
            second.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
