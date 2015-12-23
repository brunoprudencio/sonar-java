/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks.verifier;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.fest.assertions.Fail;
import org.sonar.java.JavaConfiguration;
import org.sonar.java.ast.JavaAstScanner;
import org.sonar.java.ast.visitors.SubscriptionVisitor;
import org.sonar.java.model.JavaVersionImpl;
import org.sonar.java.model.VisitorsBridgeForTests;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaVersion;
import org.sonar.plugins.java.api.tree.SyntaxTrivia;
import org.sonar.plugins.java.api.tree.Tree;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;


/**
 * It is possible to specify the absolute line number on which the issue should appear by appending {@literal "@<line>"} to "Noncompliant".
 * But usually better to use line number relative to the current, this is possible to do by prefixing the number with either '+' or '-'.
 * For example:
 * <pre>
 *   // Noncompliant@+1 {{do not import "java.util.List"}}
 *   import java.util.List;
 * </pre>
 * Full syntax:
 * <pre>
 *   // Noncompliant@+1 [[startColumn=1;endLine=+1;endColumn=2;effortToFix=4;secondary=3,4]] {{issue message}}
 * </pre>
 * Attributes between [[]] are optional:
 * <ul>
 *   <li>startColumn: column where the highlight starts</li>
 *   <li>endLine: relative endLine where the highlight ends (i.e. +1), same line if omitted</li>
 *   <li>endColumn: column where the highlight ends</li>
 *   <li>effortToFix: the cost to fix as integer</li>
 *   <li>secondary: a comma separated list of integers identifying the lines of secondary locations if any</li>
 * </ul>
 */
@Beta
public class JavaCheckVerifier extends CheckVerifier {

  /**
   * Default location of the jars/zips to be taken into account when performing the analysis.
   */
  private static final String DEFAULT_TEST_JARS_DIRECTORY = "target/test-jars";
  private String testJarsDirectory;
  private boolean providedJavaVersion = false;
  private JavaVersion javaVersion = new JavaVersionImpl();

  private JavaCheckVerifier() {
    this.testJarsDirectory = DEFAULT_TEST_JARS_DIRECTORY;
  }

  @Override
  public String getExpectedIssueTrigger() {
    return "// " + ISSUE_MARKER;
  }

  /**
   * Verifies that the provided file will raise all the expected issues when analyzed with the given check.
   *
   * <br /><br />
   *
   * By default, any jar or zip archive present in the folder defined by {@link JavaCheckVerifier#DEFAULT_TEST_JARS_DIRECTORY} will be used
   * to add extra classes to the classpath. If this folder is empty or does not exist, then the analysis will be based on the source of
   * the provided file.
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   */
  public static void verify(String filename, JavaFileScanner check) {
    scanFile(filename, check, new JavaCheckVerifier());
  }

  /**
   * Verifies that the provided file will raise all the expected issues when analyzed with the given check and a given
   * java version used for the sources.
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   * @param javaVersion The version to consider for the analysis (6 for java 1.6, 7 for 1.7, etc.)
   */
  public static void verify(String filename, JavaFileScanner check, int javaVersion) {
    JavaCheckVerifier javaCheckVerifier = new JavaCheckVerifier();
    javaCheckVerifier.providedJavaVersion = true;
    javaCheckVerifier.javaVersion = new JavaVersionImpl(javaVersion);
    scanFile(filename, check, javaCheckVerifier);
  }

  /**
   * Verifies that the provided file will raise all the expected issues when analyzed with the given check,
   * but using having the classpath extended with a collection of files (classes/jar/zip).
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   * @param classpath The files to be used as classpath
   */
  public static void verify(String filename, JavaFileScanner check, Collection<File> classpath) {
    scanFile(filename, check, new JavaCheckVerifier(), classpath);
  }

  /**
   * Verifies that the provided file will raise all the expected issues when analyzed with the given check,
   * using jars/zips files from the given directory to extends the classpath.
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   * @param testJarsDirectory The directory containing jars and/or zip defining the classpath to be used
   */
  public static void verify(String filename, JavaFileScanner check, String testJarsDirectory) {
    JavaCheckVerifier javaCheckVerifier = new JavaCheckVerifier();
    javaCheckVerifier.testJarsDirectory = testJarsDirectory;
    scanFile(filename, check, javaCheckVerifier);
  }

  /**
   * Verifies that the provided file will not raise any issue when analyzed with the given check.
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   */
  public static void verifyNoIssue(String filename, JavaFileScanner check) {
    JavaCheckVerifier javaCheckVerifier = new JavaCheckVerifier();
    javaCheckVerifier.expectNoIssues();
    scanFile(filename, check, javaCheckVerifier);
  }

  /**
   * Verifies that the provided file will not raise any issue when analyzed with the given check.
   *
   * @param filename The file to be analyzed
   * @param check The check to be used for the analysis
   * @param javaVersion The version to consider for the analysis (6 for java 1.6, 7 for 1.7, etc.)
   */
  public static void verifyNoIssue(String filename, JavaFileScanner check, int javaVersion) {
    JavaCheckVerifier javaCheckVerifier = new JavaCheckVerifier();
    javaCheckVerifier.expectNoIssues();
    javaCheckVerifier.providedJavaVersion = true;
    javaCheckVerifier.javaVersion = new JavaVersionImpl(javaVersion);
    scanFile(filename, check, javaCheckVerifier);
  }

  /**
   * Verifies that the provided file will only raise an issue on the file, with the given message, when analyzed using the given check.
   *
   * @param filename The file to be analyzed
   * @param message The message expected to be raised on the file
   * @param check The check to be used for the analysis
   */
  public static void verifyIssueOnFile(String filename, String message, JavaFileScanner check) {
    JavaCheckVerifier javaCheckVerifier = new JavaCheckVerifier();
    javaCheckVerifier.setExpectedFileIssue(message);
    scanFile(filename, check, javaCheckVerifier);
  }

  private static void scanFile(String filename, JavaFileScanner check, JavaCheckVerifier javaCheckVerifier) {
    Collection<File> classpath = Lists.newLinkedList();
    File testJars = new File(javaCheckVerifier.testJarsDirectory);
    if (testJars.exists()) {
      classpath = FileUtils.listFiles(testJars, new String[]{"jar", "zip"}, true);
    } else if (!DEFAULT_TEST_JARS_DIRECTORY.equals(javaCheckVerifier.testJarsDirectory)) {
      Fail.fail("The directory to be used to extend class path does not exists (" + testJars.getAbsolutePath() + ").");
    }
    classpath.add(new File("target/test-classes"));
    scanFile(filename, check, javaCheckVerifier, classpath);
  }

  private static void scanFile(String filename, JavaFileScanner check, JavaCheckVerifier javaCheckVerifier, Collection<File> classpath) {
    JavaFileScanner expectedIssueCollector = new ExpectedIssueCollector(javaCheckVerifier);
    VisitorsBridgeForTests visitorsBridge = new VisitorsBridgeForTests(Lists.newArrayList(check, expectedIssueCollector), Lists.newArrayList(classpath), null);
    JavaConfiguration conf = new JavaConfiguration(Charset.forName("UTF-8"));
    conf.setJavaVersion(javaCheckVerifier.javaVersion);
    JavaAstScanner.scanSingleFileForTests(new File(filename), visitorsBridge, conf);
    VisitorsBridgeForTests.TestJavaFileScannerContext testJavaFileScannerContext = visitorsBridge.lastCreatedTestContext();
    javaCheckVerifier.checkIssues(testJavaFileScannerContext.getIssues(), javaCheckVerifier.providedJavaVersion);
  }

  private static class ExpectedIssueCollector extends SubscriptionVisitor {

    private final JavaCheckVerifier verifier;

    public ExpectedIssueCollector(JavaCheckVerifier verifier) {
      this.verifier = verifier;
    }

    @Override
    public List<Tree.Kind> nodesToVisit() {
      return ImmutableList.of(Tree.Kind.TRIVIA);
    }

    @Override
    public void visitTrivia(SyntaxTrivia syntaxTrivia) {
      verifier.collectExpectedIssues(syntaxTrivia.comment(), syntaxTrivia.startLine());
    }
  }
}
