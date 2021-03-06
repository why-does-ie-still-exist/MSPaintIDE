package com.uddernetworks.mspaint.main;

import com.uddernetworks.mspaint.highlighter.AngrySquiggleHighlighter;
import com.uddernetworks.mspaint.imagestreams.ImageOutputStream;
import com.uddernetworks.mspaint.languages.Language;
import com.uddernetworks.mspaint.languages.LanguageError;
import com.uddernetworks.mspaint.languages.LanguageManager;
import com.uddernetworks.mspaint.languages.brainfuck.BrainfuckLanguage;
import com.uddernetworks.mspaint.languages.java.JavaLanguage;
import com.uddernetworks.mspaint.languages.python.PythonLanguage;
import com.uddernetworks.mspaint.main.settings.Setting;
import com.uddernetworks.mspaint.main.settings.SettingsManager;
import com.uddernetworks.mspaint.project.PPFProject;
import com.uddernetworks.mspaint.project.ProjectManager;
import com.uddernetworks.newocr.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Main {

    private File parent;
    private File currentJar;

    private MainGUI mainGUI;

    private List<ImageClass> imageClasses = new ArrayList<>();

    private LanguageManager languageManager = new LanguageManager();
    private Language currentLanguage;
    private DatabaseManager databaseManager;

    public void start(MainGUI mainGUI) throws IOException, URISyntaxException {
        headlessStart();
        this.mainGUI = mainGUI;
        currentJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        parent = currentJar.getParentFile();

        this.mainGUI.setDarkTheme(SettingsManager.getSetting(Setting.DARK_THEME, Boolean.class));
        this.mainGUI.updateTheme();

        languageManager.addLanguage(new JavaLanguage());
        languageManager.addLanguage(new BrainfuckLanguage());
        languageManager.addLanguage(new PythonLanguage());

        languageManager.initializeLanguages();
        mainGUI.addLanguages(languageManager.getEnabledLanguages());
    }

    public void headlessStart() throws IOException {
        SettingsManager.initialize(new File(MainGUI.LOCAL_MSPAINT, "options.ini"));
        this.databaseManager = new DatabaseManager(SettingsManager.getSetting(Setting.DATABASE_URL, String.class), SettingsManager.getSetting(Setting.DATABASE_USER, String.class), SettingsManager.getSetting(Setting.DATABASE_PASS, String.class));
    }

    public void setCurrentLanguage(Language language) {
        this.currentLanguage = language;
    }

    public Language getCurrentLanguage() {
        return this.currentLanguage;
    }

    private boolean optionsNotFilled() {
        PPFProject ppfProject = ProjectManager.getPPFProject();
        return ppfProject.getInputLocation() == null || ppfProject.getClassLocation() == null || ppfProject.getCompilerOutput() == null || ppfProject.getCompilerOutput() == null;
    }

    public int indexAll(boolean useCaches, boolean saveCaches) {
        if (optionsNotFilled()) {
            System.err.println("Please select files for all options");
            mainGUI.setHaveError();
            return -1;
        }

        System.out.println("Scanning all images...");
        long start = System.currentTimeMillis();

        mainGUI.setStatusText("Indexing letters...");

        mainGUI.setStatusText(null);

        File inputImage = ProjectManager.getPPFProject().getInputLocation();
        File objectFile = ProjectManager.getPPFProject().getObjectLocation();

        if (inputImage.isDirectory()) {
            System.out.println("Found directory: " + inputImage.getAbsolutePath());
            for (File imageFile : getFilesFromDirectory(inputImage, this.currentLanguage.getFileExtensions(), "png")) {
                System.out.println("2 Adding non directory: " + imageFile.getAbsolutePath());
                imageClasses.add(new ImageClass(imageFile, objectFile, mainGUI, useCaches, saveCaches));
            }
        } else {
            System.out.println("Adding non directory: " + inputImage.getAbsolutePath());
            imageClasses.add(new ImageClass(inputImage, objectFile, mainGUI, useCaches, saveCaches));
        }

        mainGUI.setStatusText(null);

        System.out.println("Finished scanning all images in " + (System.currentTimeMillis() - start) + "ms");
        return 1;
    }

    public void highlightAll() throws IOException {
        if (optionsNotFilled()) {
            System.err.println("Please select files for all options");
            mainGUI.setHaveError();
            return;
        }

        File highlightedFile = ProjectManager.getPPFProject().getHighlightLocation();

        if (highlightedFile != null && !highlightedFile.isDirectory()) highlightedFile.mkdirs();

        if (highlightedFile == null || !highlightedFile.isDirectory()) {
            System.err.println("No highlighted file directory found!");
            mainGUI.setHaveError();
            return;
        }

        System.out.println("Scanning all images...");
        mainGUI.setStatusText("Highlighting...");
        mainGUI.setIndeterminate(true);
        long start = System.currentTimeMillis();

        for (ImageClass imageClass : imageClasses) {
            imageClass.highlight(highlightedFile);
        }

        mainGUI.setIndeterminate(false);
        mainGUI.setStatusText(null);

        System.out.println("Finished highlighting all images in " + (System.currentTimeMillis() - start) + "ms");
    }


    public void compile(boolean execute) throws IOException {
        long start = System.currentTimeMillis();

        if (getCurrentLanguage().isInterpreted()) {
            System.out.println("Interpreting...");
            mainGUI.setStatusText("Interpreting...");
        } else {
            System.out.println("Compiling...");
            mainGUI.setStatusText("Compiling...");
        }

        File libraryFile = ProjectManager.getPPFProject().getLibraryLocation();

        mainGUI.setIndeterminate(true);

        List<File> libFiles = new ArrayList<>();
        if (libraryFile != null) {
            if (libraryFile.isFile()) {
                if (libraryFile.getName().endsWith(".jar")) {
                    libFiles.add(libraryFile);
                }
            } else {
                libFiles.addAll(getFilesFromDirectory(libraryFile, "jar"));
            }
        }

        ImageOutputStream imageOutputStream = new ImageOutputStream(ProjectManager.getPPFProject().getAppOutput(), 500);
        ImageOutputStream compilerOutputStream = new ImageOutputStream(ProjectManager.getPPFProject().getCompilerOutput(), 500);
        Map<ImageClass, List<LanguageError>> errors = getCurrentLanguage().compileAndExecute(imageClasses, ProjectManager.getPPFProject().getJarFile(), ProjectManager.getPPFProject().getOtherLocation(), ProjectManager.getPPFProject().getClassLocation(), mainGUI, imageOutputStream, compilerOutputStream, libFiles, execute);

        System.out.println("Highlighting Angry Squiggles...");
        mainGUI.setStatusText("Highlighting Angry Squiggles...");

        for (ImageClass imageClass : errors.keySet()) {
            AngrySquiggleHighlighter highlighter = new AngrySquiggleHighlighter(imageClass.getImage(), 3, imageClass.getHighlightedFile(), imageClass.getScannedImage(), errors.get(imageClass));
            highlighter.highlightAngrySquiggles();
        }

        System.out.println("Saving output images...");
        mainGUI.setStatusText("Saving output images...");

        imageOutputStream.saveImage();
        compilerOutputStream.saveImage();

        mainGUI.setStatusText(null);

        System.out.println("Finished " + (getCurrentLanguage().isInterpreted() ? "interpreting" : "compiling") + " in " + (System.currentTimeMillis() - start) + "ms");

        imageClasses.clear();
    }

    public List<File> getFilesFromDirectory(File directory, String extension) {
        return getFilesFromDirectory(directory, new String[] {extension});
    }

    public List<File> getFilesFromDirectory(File directory, String[] extensions) {
        List<File> ret = new ArrayList<>();
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                ret.addAll(getFilesFromDirectory(file, extensions));
            } else {
                if (extensions == null || Arrays.stream(extensions).anyMatch(extension -> file.getName().endsWith("." + extension))) ret.add(file);
            }
        }

        return ret;
    }

    public List<File> getFilesFromDirectory(File directory, String[] extensions, String postExtension) {
        return getFilesFromDirectory(directory, Arrays.stream(extensions).map(string -> string + "." + postExtension).toArray(String[]::new));
    }

    public void setInputImage(File inputImage) {
        PPFProject ppfProject = ProjectManager.getPPFProject();
        if (inputImage.equals(ppfProject.getInputLocation())) return;

        File outputParent = inputImage.getParentFile();


        ppfProject.setHighlightLocation(new File(outputParent, "highlighted"), false);
        ppfProject.setCompilerOutput(new File(outputParent, "compiler.png"), false);
        ppfProject.setAppOutput(new File(outputParent, "program.png"), false);
        ppfProject.setJarFile(this.currentLanguage.getOutputFileExtension() == null ? null : new File(outputParent, "Output." + this.currentLanguage.getOutputFileExtension()), false);
        ppfProject.setClassLocation(new File(outputParent, "classes"), false);

        ProjectManager.save();
        this.mainGUI.initializeInputTextFields();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
