package com.hms_networks.americas.sc.javaloader;

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
 * @version 1.0.0
 * @since 1.0.0
 */
public class EwonJavaMultiLoaderMain {

  /**
   * Main method for the Ewon Java Multi-Loader application. Each Java jar file within the {@link
   * #MULTI_LOADER_CLASSPATH_FOLDER} directory of the Ewon filesystem is loaded to the Java
   * classpath, then each main class/method in is started in a new thread.
   *
   * <p>Note: All log output messages are written using the {@link Logger#LOG_LEVEL_CRITICAL} log
   * level to ensure no output is skipped due to other application interaction with the {@link
   * Logger} class.
   *
   * @param args command line arguments (not used, ignored)
   * @since 1.0.0
   */
  public static void main(String[] args) {
    // Try to output application name from Maven
    System.out.println("App name: " + EwonJavaMultiLoaderMain.class.getPackage().getImplementationTitle());

    // Try to output application version from Maven
    System.out.println(
        "App version: " + EwonJavaMultiLoaderMain.class.getPackage().getImplementationVersion());
  }
}
