/**
 * Copyright (c) 2013 Source Auditor Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.licenseTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.spdx.rdfparser.license.LicenseParserException;

// adann
// FIXME: this is a dirty hack, to maintain newlines
// this overrides the default implementation, because it occurs first in the classpath

/**
 * Implements common conversion methods for processing SPDX license templates
 *
 * @author Gary O'Neall
 */
public class SpdxLicenseTemplateHelper {

  static final String START_RULE = "<<";
  static final String END_RULE = ">>";
  public static final Pattern RULE_PATTERN =
      Pattern.compile(START_RULE + "\\s*((beginOptional|endOptional|var)(.|\\s)*?)\\s*" + END_RULE);
  private static final int SPACES_PER_TAB = 5;
  private static final int MAX_TABS = 4;
  private static final int[] PIXELS_PER_TAB = new int[] {20, 40, 60, 70};

  /**
   * Parses the license template calling the templateOutputHandler for any text and rules found
   *
   * @param licenseTemplate License template to be parsed
   * @param templateOutputHandler Handles the text, optional text, and variable rules text found
   * @throws LicenseParserException
   */
  public static void parseTemplate(
      String licenseTemplate, ILicenseTemplateOutputHandler templateOutputHandler)
      throws LicenseTemplateRuleException, LicenseParserException {
    Matcher ruleMatcher = RULE_PATTERN.matcher(licenseTemplate);
    int end = 0;
    int optionalNestLevel = 0;
    while (ruleMatcher.find()) {
      // copy everything up to the start of the find
      String upToTheFind = licenseTemplate.substring(end, ruleMatcher.start());
      if (!upToTheFind.trim().isEmpty()) {
        templateOutputHandler.text(upToTheFind);
      }
      end = ruleMatcher.end();
      String ruleString = ruleMatcher.group(1);
      LicenseTemplateRule rule = new LicenseTemplateRule(ruleString);
      if (rule.getType() == LicenseTemplateRule.RuleType.VARIABLE) {
        templateOutputHandler.variableRule(rule);
      } else if (rule.getType() == LicenseTemplateRule.RuleType.BEGIN_OPTIONAL) {
        templateOutputHandler.beginOptional(rule);
        optionalNestLevel++;
      } else if (rule.getType() == LicenseTemplateRule.RuleType.END_OPTIONAL) {
        optionalNestLevel--;
        if (optionalNestLevel < 0) {
          throw (new LicenseTemplateRuleException(
              "End optional rule found without a matching begin optional rule after text '"
                  + upToTheFind
                  + "'"));
        }
        templateOutputHandler.endOptional(rule);
      } else {
        throw (new LicenseTemplateRuleException(
            "Unrecognized rule: "
                + rule.getType().toString()
                + " after text '"
                + upToTheFind
                + "'"));
      }
    }
    if (optionalNestLevel > 0) {
      throw (new LicenseTemplateRuleException("Missing EndOptional rule and end of text"));
    }
    // copy the rest of the template to the end
    String restOfTemplate = licenseTemplate.substring(end);
    if (!restOfTemplate.isEmpty()) {
      templateOutputHandler.text(restOfTemplate);
    }
    templateOutputHandler.completeParsing();
  }

  /**
   * Converts a license template string to formatted HTML which highlights any rules or tags
   *
   * @param licenseTemplate
   * @return
   * @throws LicenseTemplateRuleException
   * @throws LicenseParserException
   */
  public static String templateTextToHtml(String licenseTemplate)
      throws LicenseTemplateRuleException {
    HtmlTemplateOutputHandler htmlOutput = new HtmlTemplateOutputHandler();
    try {
      parseTemplate(licenseTemplate, htmlOutput);
    } catch (LicenseParserException e) {
      throw new LicenseTemplateRuleException("Parsing error parsing license template", e);
    }
    return htmlOutput.getHtml();
  }

  /**
   * Converts template text to standard default text using any default parameters in the rules
   *
   * @param template
   * @return
   * @throws LicenseTemplateRuleException
   * @throws LicenseParserException
   */
  public static String templateToText(String template) throws LicenseTemplateRuleException {
    TextTemplateOutputHandler textOutput = new TextTemplateOutputHandler();
    try {
      parseTemplate(template, textOutput);
    } catch (LicenseParserException e) {
      throw new LicenseTemplateRuleException("Parsing error parsing license template", e);
    }
    return textOutput.getText();
  }

  /**
   * Escapes and formats text
   *
   * @param text unformatted text
   * @return
   */
  public static String formatEscapeHTML(String text) {
    return formatEscapeHTML(text, false);
  }

  /**
   * Escapes and formats text
   *
   * @param text unformatted text
   * @param inParagraph true if inside a paragraph tag
   * @return
   */
  public static String formatEscapeHTML(String text, boolean inParagraph) {
    String retval = StringEscapeUtils.escapeXml(text);
    return addHtmlFormatting(retval, inParagraph);
  }

  /**
   * Adds HTML formatting <br>
   * and
   *
   * <p>
   *
   * @param text unformatted text
   * @return
   */
  public static String addHtmlFormatting(String text) {
    return addHtmlFormatting(text, false);
  }

  /**
   * Adds HTML formatting <br>
   * and
   *
   * <p>
   *
   * @param text unformatted text
   * @param inParagraph true if inside a paragraph tag
   * @return
   */
  public static String addHtmlFormatting(String text, boolean inParagraph) {
    String[] lines = text.split("\n");
    StringBuilder result = new StringBuilder();
    result.append(lines[0]);
    int i = 1;
    while (i < lines.length) {
      if (lines[i].trim().isEmpty()) {
        // paragraph boundary
        if (inParagraph) {
          result.append("</p>");
        }
        result.append("\n");
        i++;
        if (i < lines.length) {
          String paragraphTag = getParagraphTagConsideringTags(lines[i]);
          result.append(paragraphTag);
          result.append(lines[i++]);
        } else {
          result.append("<p>");
        }
        inParagraph = true;
      } else {
        // just a line break
        result.append("<br/>");
        result.append("\n");
        result.append(lines[i++]);
      }
    }
    if (inParagraph) {
      result.append("</p>");
    } else if (text.endsWith("\n")) {
      result.append("<br/>\n");
    }
    return result.toString();
  }

  /**
   * Creating a paragraph tag and add the correct margin considering the number of spaces or tabs
   *
   * @param line
   * @return
   */
  private static String getParagraphTagConsideringTags(String line) {
    int numSpaces = countLeadingSpaces(line);
    StringBuilder result = new StringBuilder();
    if (numSpaces >= SPACES_PER_TAB) {
      int numTabs = numSpaces / SPACES_PER_TAB;
      if (numTabs > MAX_TABS) {
        numTabs = MAX_TABS;
      }

      int pixels = PIXELS_PER_TAB[numTabs - 1];
      result.append("<p style=\"margin-left: ");
      result.append(String.valueOf(pixels));
      result.append("px;\">");
    } else {
      result.append("<p>");
    }
    return result.toString();
  }

  /**
   * Counts the number of leading spaces in a given line
   *
   * @param string
   * @return
   */
  private static int countLeadingSpaces(String string) {
    char[] charArray = string.toCharArray();
    int retval = 0;
    while (retval < charArray.length && charArray[retval] == ' ') {
      retval++;
    }
    return retval;
  }

  /**
   * Converts an HTML string to text preserving line breaks for <br>
   * tags
   *
   * @param html
   * @return
   */
  // complete overvwrite - by addan
  // fixme: make this a PR for https://github.com/spdx/tools/blob/master/pom.xml
  public static String htmlToText(String html) {
    if (html == null) return html;
    Document document = Jsoup.parse(html);
    document.outputSettings(
        new Document.OutputSettings()
            .prettyPrint(false)); // makes html() preserve linebreaks and spacing
    document.select("br").append("\\n");
    document.select("p").prepend("\\n\\n");
    String s = document.html().replaceAll("\\\\n", "\n");
    return Jsoup.clean(s, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
  }
}