package com.hms_networks.americas.sc.javaloader;

import com.ewon.ewonitf.EventHandlerThread;
import com.ewon.ewonitf.RuntimeControl;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import jregex.Matcher;
import jregex.Pattern;

/**
 * Main class for the Ewon Java Multi-Loader application. The main method of this class loads each
 * Java jar file within the {@link #MULTI_LOADER_CLASSPATH_FOLDER} directory of the Ewon filesystem
 * to the Java classpath, then starts each main class/method in a new thread.
 *
 * <p>The documentation in this class may reference components of Java or the Ewon Flexy using
 * abbreviated terms. These terms are not case-sensitive and are defined below:
 *
 * <ul>
 *   <li>Sc - Solution Center
 *   <li>Jvm - Java Virtual Machine
 *   <li>Cp - Classpath
 * </ul>
 *
 * @author HMS Networks, MU Americas Solution Center
 * @version 1.1.0
 * @since 1.0.0
 */
public class EwonJavaMultiLoaderMain {

  /**
   * The default heap size (in MB) to specify in jvmrun files or next run commands.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_DEFAULT_HEAPSIZE_M = "25M";

  /**
   * The argument used to specify the heap size in jvmrun files or next run commands.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_HEAPSIZE_ARG = "-heapsize";

  /**
   * The argument used to specify the classpath in jvmrun files or next run commands.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_CLASSPATH_ARG = "-classpath";

  /**
   * The argument used to specify the main class in jvmrun files or next run commands.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_MAIN_CLASS_ARG = "-emain";

  /**
   * The delimiter used to separate classpath entries in jvmrun files or next run commands.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_CLASSPATH_SEPARATOR = File.pathSeparator;

  /**
   * The file path of the jvmrun file on the Ewon filesystem.
   *
   * @since 1.0.0
   */
  private static final String JVMRUN_FILE_PATH = "/usr/jvmrun";

  /**
   * The regex pattern used to match the contents of jvmrun files or next run commands. This pattern
   * may be used to extract the heap size, classpath, and main class from a jvmrun file or next run
   * command using capture groups 1, 2, and 3 respectively.
   *
   * @since 1.0.0
   * @see #JVMRUN_HEAPSIZE_ARG
   * @see #JVMRUN_CLASSPATH_ARG
   * @see #JVMRUN_MAIN_CLASS_ARG
   */
  private static final String JVMRUN_REGEX_PATTERN =
      "^"
          + JVMRUN_HEAPSIZE_ARG
          + "\\s(\\S*)\\s"
          + JVMRUN_CLASSPATH_ARG
          + "\\s(\\S*)\\s"
          + JVMRUN_MAIN_CLASS_ARG
          + "\\s(\\S*)\\n$";

  /**
   * The index of the capture group in the {@link #JVMRUN_REGEX_PATTERN} which contains the heapsize
   * attribute.
   *
   * @since 1.0.0
   */
  private static final int JVMRUN_REGEX_HEAPSIZE_GROUP = 1;

  /**
   * The file path of the Ewon Java Multi-Loader application Java classpath folder on the Ewon
   * filesystem.
   *
   * @since 1.0.0
   */
  private static final String MULTI_LOADER_CLASSPATH_FOLDER = "/usr/MultiLoaderClasspath/";

  /**
   * The prefix used to form the name of threads which are started by this class to execute the main
   * method of an Ewon Java Multi-Loader application Java classpath folder Jar file. The thread name
   * prefix is followed by a unique number identifier for each thread. For example, a complete
   * thread name may look like "MultiLoaderExecMain-1".
   *
   * @since 1.0.0
   */
  private static final String MULTI_LOADER_JAR_MAIN_THREAD_PREFIX = "MultiLoaderExecMain-";

  /**
   * The attribute name used to specify the main class in the manifest of a Java jar file.
   *
   * @since 1.0.0
   */
  private static final String JAR_MANIFEST_MAIN_CLASS_ATTRIBUTE = "Main-Class";

  /**
   * The regex pattern used to match a {@code <dependency>} entry for the Solution Center Extensions
   * Library in a Jar file's {@code pom.xml} file(s). This pattern may be used to extract the
   * version of the Solution Center Extensions Library using the capture group specified by {@link
   * #JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX_GROUP_INDEX}.
   *
   * @since 1.1.0
   */
  private static final String JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX =
      "^\\s*<dependency>\\s*"
          + "<groupId>com\\.hms_networks\\.americas\\.sc<\\/groupId>\\s*"
          + "<artifactId>extensions<\\/artifactId>\\s*"
          + "<version>(.*)<\\/version>\\s*"
          + "<\\/dependency>\\s*$";

  /**
   * The index of the capture group in {@link #JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX} which
   * contains the version of the Solution Center Extensions Library.
   *
   * @since 1.1.0
   */
  private static final int JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX_GROUP_INDEX = 1;

  /**
   * The file extension of Java jar files.
   *
   * @since 1.0.0
   */
  private static final String JAR_FILE_EXTENSION = ".jar";

  /**
   * The file extension of Java class files.
   *
   * @since 1.0.0
   */
  private static final String CLASS_FILE_EXTENSION = ".class";

  /**
   * The delimiter used to separate packages in the full class name of a Java class.
   *
   * @since 1.0.0
   */
  private static final char JAVA_CLASS_NAME_PACKAGE_DELIMITER = '.';

  /**
   * The delimiter used to separate packages in the resource path of a Java class.
   *
   * @since 1.0.0
   */
  private static final char JAVA_RESOURCE_PATH_PACKAGE_DELIMITER = '/';

  /**
   * The regex pattern used to match the contents of a Java resource path {@link URL}. This pattern
   * may be used to extract the containing Jar file path from a Java resource path {@link URL} using
   * the capture group defined by {@link #JAVA_RESOURCE_PATH_URL_REGEX_GROUP_JAR_FILE_PATH}.
   *
   * @since 1.0.0
   */
  private static final String JAVA_RESOURCE_PATH_URL_REGEX = "^jar:file:(.*.jar)!\\/.*$";

  /**
   * The index of the capture group in the {@link #JAVA_RESOURCE_PATH_URL_REGEX} which contains the
   * containing Jar file path of the Java resource file.
   *
   * @since 1.0.0
   */
  private static final int JAVA_RESOURCE_PATH_URL_REGEX_GROUP_JAR_FILE_PATH = 1;

  /**
   * The name of a Java main method.
   *
   * @since 1.0.0
   */
  private static final String JAVA_MAIN_METHOD_NAME = "main";

  /**
   * The parameter types of a Java main method.
   *
   * @since 1.0.0
   */
  private static final Class[] JAVA_MAIN_METHOD_PARAMETER_TYPES = new Class[] {String[].class};

  /**
   * The argument specified to an Ewon Flexy Java application to indicate that it was started by the
   * Ewon Flexy Java Multi-Loader application. The value of this argument must match the value of
   * {@code SCAppArgsParser#ARG_STARTED_BY_MULTI_LOADER} in the Solution Center Extensions Library.
   * If a mismatch occurs, unintended interactions between the Ewon Flexy Java Multi-Loader and
   * applications started by it may occur.
   *
   * @since 1.1.0
   */
  public static final String ARG_STARTED_BY_MULTI_LOADER = "-StartedByMultiLoader";

  /**
   * Main method for the Ewon Java Multi-Loader application. Each Java jar file within the {@link
   * #MULTI_LOADER_CLASSPATH_FOLDER} directory of the Ewon filesystem is loaded to the Java
   * classpath, then each main class/method in is started in a new thread.
   *
   * <p>Note: All log output messages are written using the standard Java System.out and System.err
   * print streams to ensure no output is skipped due to other application interactions.
   *
   * @param args command line arguments (not used, ignored)
   * @since 1.0.0
   */
  public static void main(String[] args) {
    // Output application start message
    System.out.println(
        "Running "
            + EwonJavaMultiLoaderMain.class.getPackage().getImplementationTitle()
            + " (v"
            + EwonJavaMultiLoaderMain.class.getPackage().getImplementationVersion()
            + ")...");

    // Create variable to track if restart message shown (not shown on auto-restart)
    boolean restart = false;

    // Create variable to track if shutdown is due to an error
    boolean errorShutdown = false;

    // Get current (running) JVM classpath jar files
    final String[] currentJvmCpJarFiles = getCurrentJvmCpJarFiles();

    // Get multi-loader classpath folder jar files
    final String[] multiLoaderCpFolderJarFiles = getMultiLoaderCpFolderJarFiles();

    // Check if current JVM classpath is missing any multi-loader classpath folder jar files
    final boolean currentJvmCpMissingMultiLoaderCpFolderJarFiles =
        checkCurrentJvmCpMissingMultiLoaderCpFolderJarFiles(
            currentJvmCpJarFiles, multiLoaderCpFolderJarFiles);

    // Handle missing multi-loader classpath folder jar files, otherwise start each Jar
    if (!currentJvmCpMissingMultiLoaderCpFolderJarFiles) {
      // Check for extensions library version mismatches
      final List extensionsLibraryVersionMismatch =
          checkExtensionsLibraryVersionMismatch(multiLoaderCpFolderJarFiles);
      if (extensionsLibraryVersionMismatch == null) {
        // Start an event manager
        boolean autorun = false;
        EventHandlerThread eventHandler = new EventHandlerThread(autorun);
        eventHandler.runEventManagerInThread();

        /* Start the main method in the main class of each multi-leader classpath folder jar in a
         * new thread */
        for (int i = 0; i < multiLoaderCpFolderJarFiles.length; i++) {
          String[] multiLoaderCpFolderJarArgs = new String[] {ARG_STARTED_BY_MULTI_LOADER};
          startThreadAndRunJarFile(i, multiLoaderCpFolderJarFiles[i], multiLoaderCpFolderJarArgs);
        }
      } else {
        // Output library version mismatch error message
        System.err.println(
            "A mismatch was detected between Solution Center Extensions Library versions which are"
                + " being loaded from Jars in the Ewon Java Multi-Loader classpath. Please ensure"
                + " that only one Jar file contains the Solution Center Extensions Library, or that"
                + " all Jars containing the Solution Center Extensions Library use the same"
                + " version.");
        System.err.println(
            "The following Jars contain the Solution Center Extensions Library, but use different"
                + " versions:");
        for (int i = 0; i < extensionsLibraryVersionMismatch.size(); i++) {
          System.err.println(extensionsLibraryVersionMismatch.get(i));
        }
        errorShutdown = true;
      }

    } else {
      /* Configure next run command to add missing (include all) multi-loader classpath folder jar
       * files */
      restart = true;
      System.out.println(
          "The "
              + EwonJavaMultiLoaderMain.class.getPackage().getImplementationTitle()
              + " application will be restarted to update the JVM classpath.");
    }

    // Output application shutdown/restart message and configure next run command
    if (!errorShutdown) {
      RuntimeControl.configureNextRunCommand(
          getNextRunCmdWithMultiLoaderCpFolderJarFiles(multiLoaderCpFolderJarFiles));
    }
    if (restart) {
      // Launcher will be immediately restarted to update the JVM classpath
      System.out.println(
          "Restarting "
              + EwonJavaMultiLoaderMain.class.getPackage().getImplementationTitle()
              + "!");
    } else {
      // Launcher successfully finished and will not be immediately restarted
      System.out.println(
          "Finished running "
              + EwonJavaMultiLoaderMain.class.getPackage().getImplementationTitle()
              + "!");
      if (!errorShutdown) {
        System.out.println(
            "Automatic restart functionality has been enabled. If the JVM stops running, the "
                + "application will be restarted.");
      }
    }
  }

  /**
   * Gets an array of the Jar files on the current (running) JVM classpath using the system class
   * loader.
   *
   * @return an array of the Jar files on the current (running) JVM classpath
   * @see ClassLoader#getSystemClassLoader()
   * @since 1.0.0
   */
  private static String[] getCurrentJvmCpJarFiles() {
    // Get system classloader and store URLs
    URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
    URL[] systemClassLoaderUrls = systemClassLoader.getURLs();

    // Get file path string for each URL and store in array
    String[] currentJvmCpJarFiles = new String[systemClassLoaderUrls.length];
    for (int x = 0; x < systemClassLoaderUrls.length; x++) {
      currentJvmCpJarFiles[x] = systemClassLoaderUrls[x].getFile();
    }

    // Return current JVM classpath jar files
    return currentJvmCpJarFiles;
  }

  /**
   * Gets an array of the Jar files in the {@link #MULTI_LOADER_CLASSPATH_FOLDER} directory of the
   * Ewon filesystem. If the {@link #MULTI_LOADER_CLASSPATH_FOLDER} directory does not exist, an
   * empty array is returned and the directory is created.
   *
   * @return an array of the Jar files in the {@link #MULTI_LOADER_CLASSPATH_FOLDER} directory of
   *     the Ewon filesystem.
   * @since 1.0.0
   */
  private static String[] getMultiLoaderCpFolderJarFiles() {
    // Create classpath folder file object
    File classpathFolder = new File(MULTI_LOADER_CLASSPATH_FOLDER);

    // Create list to store Jar file paths
    ArrayList multiLoaderCpFolderJarFileList = new ArrayList();

    // Check if classpath folder exists
    if (classpathFolder.exists()) {
      //  Loop through each file and add to classpath if it is a Jar file
      File[] classpathFolderFiles = classpathFolder.listFiles();
      if (classpathFolderFiles != null) {
        for (int i = 0; i < classpathFolderFiles.length; i++) {
          File classpathFile = classpathFolderFiles[i];
          if (classpathFile.getName().toLowerCase().endsWith(JAR_FILE_EXTENSION)) {
            multiLoaderCpFolderJarFileList.add(classpathFile.getAbsolutePath());
          }
        }
      }
    } else {
      // Create classpath folder
      classpathFolder.mkdirs();
    }

    // Return string array of .jar file paths
    return (String[]) multiLoaderCpFolderJarFileList.toArray(new String[0]);
  }

  /**
   * Checks if any multi-loader classpath folder jar files are missing from the current JVM
   * classpath and returns a boolean value indicating if one or more are missing.
   *
   * @param currentJvmCpJarFiles the current JVM classpath jar files
   * @param multiLoaderCpFolderJarFiles the multi-loader classpath folder jar files
   * @return {@code true} if one or more multi-loader classpath folder jar files are missing from
   *     the current JVM classpath, {@code false} otherwise
   * @since 1.0.0
   */
  private static boolean checkCurrentJvmCpMissingMultiLoaderCpFolderJarFiles(
      String[] currentJvmCpJarFiles, String[] multiLoaderCpFolderJarFiles) {
    // Loop through each multi-loader classpath Jar file to check if in JVM classpath
    boolean missingJars = false;
    for (int i = 0; i < multiLoaderCpFolderJarFiles.length; i++) {
      // Get multi-loader classpath Jar file object
      String multiLoaderCpFolderJarFile = multiLoaderCpFolderJarFiles[i];

      // Search for multi-loader classpath Jar file in JVM classpath
      boolean jarFound = false;
      for (int j = 0; j < currentJvmCpJarFiles.length && !jarFound; j++) {
        String currentJvmCpJarFile = currentJvmCpJarFiles[j];
        jarFound = multiLoaderCpFolderJarFile.equals(currentJvmCpJarFile);
      }

      /* Log if multi-loader classpath Jar file is found on JVM classpath and set missing jars flag
       * if not */
      if (!jarFound) {
        missingJars = true;
        System.out.println("Needs to be Loaded (missing): " + multiLoaderCpFolderJarFile);
      } else {
        System.out.println("Loaded (ready): " + multiLoaderCpFolderJarFile);
      }
    }

    // Return missing jars flag
    return missingJars;
  }

  /**
   * Gets the file path of the current Jar file associated with this {@link EwonJavaMultiLoaderMain}
   * class.
   *
   * @return the file path of the current Jar file associated with this {@link
   *     EwonJavaMultiLoaderMain} class
   * @since 1.0.0
   */
  private static String getCurrentJarFilePath() {
    // Get Java resource path for class
    URL javaResourcePathUrl =
        ClassLoader.getSystemClassLoader()
            .getResource(
                EwonJavaMultiLoaderMain.class
                        .getName()
                        .replace(
                            JAVA_CLASS_NAME_PACKAGE_DELIMITER, JAVA_RESOURCE_PATH_PACKAGE_DELIMITER)
                    + CLASS_FILE_EXTENSION);

    // Extract Jar file path from Java resource path
    String jarFilePath = null;
    if (javaResourcePathUrl != null) {
      Pattern jarResourcePathUrlPattern = new Pattern(JAVA_RESOURCE_PATH_URL_REGEX);
      Matcher jarResourcePathUrlMatcher =
          jarResourcePathUrlPattern.matcher(javaResourcePathUrl.toString());
      if (jarResourcePathUrlMatcher.find()) {
        jarFilePath =
            jarResourcePathUrlMatcher.group(JAVA_RESOURCE_PATH_URL_REGEX_GROUP_JAR_FILE_PATH);
      }
    }

    // Return Jar file path or null if not found
    return jarFilePath;
  }

  /**
   * Reads the contents of the file at the specified file path and returns the contents as a String.
   *
   * @param filePath the path of the file to read
   * @return the contents of the file at the specified file path
   * @throws IOException if the file does not exist, is a directory rather than a regular file, or
   *     otherwise cannot be opened for reading
   * @since 1.1.0
   */
  private static String readFileContentsToString(String filePath) throws IOException {
    File file = new File(filePath);
    byte[] fileBytes = new byte[(int) file.length()];
    FileInputStream fileInputStream = new FileInputStream(file);
    fileInputStream.read(fileBytes);
    fileInputStream.close();
    return new String(fileBytes);
  }

  /**
   * Gets the next run command which will launch the Ewon Java Multi-Loader application with all
   * multi-loader classpath folder jar files included on the JVM classpath. If a jvmrun file exists
   * on the Ewon Flexy, the heapsize will be retained from the existing jvmrun file. If a jvmrun
   * file does not exist, a default heapsize will be used, which is specified by the {@link
   * #JVMRUN_DEFAULT_HEAPSIZE_M} constant.
   *
   * @param multiLoaderCpFolderJarFiles the multi-loader classpath folder jar files
   * @return the next run command which will launch the Ewon Java Multi-Loader application with all
   *     multi-loader classpath folder jar files included on the JVM classpath. The heapsize will be
   *     retained from an existing jvmrun file if one exists, otherwise a default heapsize will be
   *     used.
   * @since 1.0.0
   */
  private static String getNextRunCmdWithMultiLoaderCpFolderJarFiles(
      String[] multiLoaderCpFolderJarFiles) {
    // Get current jvmrun file contents
    String currentJvmrunFileContents = null;
    try {
      currentJvmrunFileContents = readFileContentsToString(JVMRUN_FILE_PATH);
    } catch (IOException e) {
      System.err.println("Could not read existing jvmrun file! It may be missing or corrupted.");
      e.printStackTrace(System.err);
    }

    // Create string to store next run command contents
    String nextRunCommand = null;

    // Get current jar file path
    String currentJarFilePath = getCurrentJarFilePath();

    // Build next run command if current jar file path is not null, otherwise log error
    if (currentJarFilePath != null) {
      // Use current jvmrun file heapsize, otherwise default
      String heapsize = JVMRUN_DEFAULT_HEAPSIZE_M;
      if (currentJvmrunFileContents != null) {
        Pattern pattern = new Pattern(JVMRUN_REGEX_PATTERN);
        Matcher matcher = pattern.matcher(currentJvmrunFileContents);
        if (matcher.find()) {
          heapsize = matcher.group(JVMRUN_REGEX_HEAPSIZE_GROUP);
        }
      }

      // Build classpath with all Jar files from multi-loader classpath folder
      StringBuffer classpath = new StringBuffer(currentJarFilePath);
      if (multiLoaderCpFolderJarFiles != null && multiLoaderCpFolderJarFiles.length > 0) {
        for (int i = 0; i < multiLoaderCpFolderJarFiles.length; i++) {
          classpath.append(JVMRUN_CLASSPATH_SEPARATOR).append(multiLoaderCpFolderJarFiles[i]);
        }
      }

      // Add heapsize to next run command
      nextRunCommand = JVMRUN_HEAPSIZE_ARG + " " + heapsize + " ";

      // Add classpath to next run command
      nextRunCommand += JVMRUN_CLASSPATH_ARG + " " + classpath + " ";

      // Add this main class to next run command
      nextRunCommand += JVMRUN_MAIN_CLASS_ARG + " " + EwonJavaMultiLoaderMain.class.getName();
    } else {
      System.err.println("Unable to detect current jar file path to build next run command!");
    }

    // Return built next run command
    return nextRunCommand;
  }

  /**
   * Checks each multi-loader classpath folder jar file to see if it contains a mismatch between
   * versions of the Solution Center Extensions Library. If a mismatch is found, a {@link String}
   * will be added to the returned {@link List} containing the mismatched jar file name and the
   * version of the Solution Center Extensions Library that it contains. If no mismatches are found,
   * the return value will be null.
   *
   * @param multiLoaderCpFolderJarFiles the multi-loader classpath folder jar files to check
   * @return a {@link List} of {@link String}s containing the names of jar files that contain a
   *     mismatch between versions of the Solution Center Extensions Library, or null if no
   *     mismatches are found
   * @since 1.1.0
   */
  private static List checkExtensionsLibraryVersionMismatch(String[] multiLoaderCpFolderJarFiles) {
    // Loop through each multi-loader classpath jar file and compare extension library version
    String lastVersion = null;
    List mismatchedVersions = null;
    List matchedVersions = new ArrayList();
    for (int i = 0; i < multiLoaderCpFolderJarFiles.length; i++) {
      String multiLoaderCpFolderJarFile = multiLoaderCpFolderJarFiles[i];
      try {
        // Get multi-loader classpath Jar file object
        JarFile jarFile = new JarFile(multiLoaderCpFolderJarFile);

        // Check for pom.xml files
        Enumeration jarFileEnumeration = jarFile.entries();
        while (jarFileEnumeration.hasMoreElements()) {
          JarEntry jarEntry = (JarEntry) jarFileEnumeration.nextElement();
          if (jarEntry.getName().endsWith("pom.xml")) {
            // Get pom.xml file input stream
            InputStream pomXmlInputStream = jarFile.getInputStream(jarEntry);

            // Read pom.xml file contents
            final int pomXmlFileContentsBufferSize = 1024;
            ByteArrayOutputStream pomXmlFileContentsOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[pomXmlFileContentsBufferSize];
            int bytesRead;
            while ((bytesRead = pomXmlInputStream.read(buffer)) != -1) {
              pomXmlFileContentsOutputStream.write(buffer, 0, bytesRead);
            }
            String pomXmlFileContents = pomXmlFileContentsOutputStream.toString();

            // Get extension library version
            Pattern pattern =
                new Pattern(JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(pomXmlFileContents);
            boolean find = matcher.find();
            if (find) {
              String version = matcher.group(JAR_POM_SC_EXTENSIONS_DEP_VERSION_REGEX_GROUP_INDEX);
              if (lastVersion == null) {
                lastVersion = version;
                matchedVersions.add(multiLoaderCpFolderJarFile + ": v" + version);
              } else if (!lastVersion.equals(version)) {
                if (mismatchedVersions == null) {
                  mismatchedVersions = new ArrayList();
                }
                mismatchedVersions.add(multiLoaderCpFolderJarFile + ": v" + version);
                break;
              }
            }
          }
        }

      } catch (Exception e) {
        System.err.println(
            "Failed to check Jar file for extension library version mismatch: "
                + multiLoaderCpFolderJarFile);
        e.printStackTrace(System.err);
      }
    }

    // If there are mismatched versions, return list of matched versions to compare against
    if (mismatchedVersions != null) {
      mismatchedVersions.addAll(matchedVersions);
    }

    return mismatchedVersions;
  }

  /**
   * Gets the name of the main class for the Jar file at the specified path.
   *
   * @param jarFilePath the path of the Jar file to get the main class name of
   * @return the name of the main class for the Jar file at the specified path
   * @since 1.0.0
   */
  private static String getJarFileMainClassName(String jarFilePath) {
    String jarFileMainClassName = null;
    try {
      JarFile jarFile = new JarFile(jarFilePath);
      Manifest manifest = jarFile.getManifest();
      Attributes attributes = manifest.getMainAttributes();
      jarFileMainClassName = attributes.getValue(JAR_MANIFEST_MAIN_CLASS_ATTRIBUTE);
    } catch (IOException e) {
      System.err.println(
          "Unable to find attribute '"
              + JAR_MANIFEST_MAIN_CLASS_ATTRIBUTE
              + "' in manifest of Jar file: "
              + jarFilePath);
      e.printStackTrace(System.err);
    }
    return jarFileMainClassName;
  }

  /**
   * Runs the main method of the Jar file at the specified path using the specified main class name
   * and arguments.
   *
   * @param jarFilePath the path of the Jar file to run the main method of
   * @param mainClassName the name of the main class to run the main method of
   * @param args the arguments to pass to the main method
   * @since 1.0.0
   * @see #startThreadAndRunJarFile(int, String, String[])
   */
  private static void runJarFileMain(String jarFilePath, String mainClassName, String[] args) {
    // Load main class by name
    Class mainClass = null;
    try {
      mainClass = Class.forName(mainClassName);
    } catch (ClassNotFoundException e) {
      System.err.println(
          "Unable to find or load main class '"
              + mainClassName
              + "' from Jar file: "
              + jarFilePath);
      e.printStackTrace(System.err);
    }

    // Load main method from main class
    Method mainMethod = null;
    try {
      if (mainClass != null) {
        mainMethod = mainClass.getMethod(JAVA_MAIN_METHOD_NAME, JAVA_MAIN_METHOD_PARAMETER_TYPES);
      }
    } catch (NoSuchMethodException e) {
      System.err.println(
          "Unable to find or load main method in main class '"
              + mainClassName
              + "' from Jar file: "
              + jarFilePath);
      e.printStackTrace(System.err);
    }

    // Run main method from main class
    if (mainClass != null && mainMethod != null) {
      try {
        mainMethod.invoke(null, new Object[] {args});
      } catch (IllegalAccessException e) {
        System.err.println(
            "Unable to access main method in main class '"
                + mainClassName
                + "' from Jar file: "
                + jarFilePath);
        e.printStackTrace(System.err);
      } catch (InvocationTargetException e) {
        System.err.println(
            "Unable to invoke main method in main class '"
                + mainClassName
                + "' from Jar file: "
                + jarFilePath);
        e.printStackTrace(System.err);
      } catch (Exception e) {
        System.err.println(
            "Could not successfully execute the main method in the main class '"
                + mainClassName
                + "' of Jar file '"
                + jarFilePath
                + "' due to an exception.");
        e.printStackTrace(System.err);
      }
    } else {
      System.err.println(
          "Cannot execute the following Jar file because the main class and/or main "
              + "method could not be found or loaded: "
              + jarFilePath);
    }
  }

  /**
   * Creates a new thread which will execute the main method of the Jar file at the specified path
   * via {@link #runJarFileMain(String, String, String[])}. The created thread will be named with
   * the prefix specified by {@link #MULTI_LOADER_JAR_MAIN_THREAD_PREFIX}, followed by the specified
   * {@code threadIndex} number value. For example, if the specified {@code threadIndex} is {@code
   * 1}, the created thread will be named {@code "EwonJavaMultiLoaderMain-1"}.
   *
   * @param threadIndex the index of the thread to create. This value will be used to name the
   *     created thread and must be unique.
   * @param jarFilePath the path of the Jar file to create a thread for and run
   * @param args the arguments to pass to the main method of the Jar file
   * @since 1.0.0
   * @see #MULTI_LOADER_JAR_MAIN_THREAD_PREFIX
   * @see #runJarFileMain(String, String, String[])
   */
  private static void startThreadAndRunJarFile(
      final int threadIndex, final String jarFilePath, final String[] args) {
    // Get name of main class from Jar file manifest
    final String jarFileMainClassName = getJarFileMainClassName(jarFilePath);

    // Start thread and run jar file if main class name is specified, otherwise log error
    if (jarFileMainClassName != null) {
      // Create thread to run Jar file
      Thread jarFileRunThread =
          new Thread(
              new Runnable() {
                public void run() {
                  // Log jar file run start
                  System.out.println("Starting " + jarFilePath + "...");

                  // Run jar file
                  runJarFileMain(jarFilePath, jarFileMainClassName, args);
                }
              },
              MULTI_LOADER_JAR_MAIN_THREAD_PREFIX + threadIndex);

      // Start thread
      jarFileRunThread.start();
    } else {
      System.err.println(
          "Cannot execute the following Jar file because it is missing a Main-Class "
              + "attribute: "
              + jarFilePath);
    }
  }
}
