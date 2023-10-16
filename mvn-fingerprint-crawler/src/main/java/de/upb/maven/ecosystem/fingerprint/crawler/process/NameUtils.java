package de.upb.maven.ecosystem.fingerprint.crawler.process;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.tagkit.InnerClassTag;
import soot.tagkit.Tag;

public class NameUtils {

  /**
   * replace illegal characters in a filename with "_" illegal characters : : \ / * ? | < >
   *
   * @param name
   * @return
   */
  public static String sanitizeFilename(String name) {
    return name.replaceAll("[:\\\\/*?|<>]", "_");
  }

  public static String className(final String qname) {
    String className = qname;
    if (qname.contains("(")) {
      // we have a method

      className = className.substring(0, className.indexOf("("));

      // check if we have a constructro --> then constname == classname
      if (Character.isUpperCase(className.charAt(className.lastIndexOf(".") + 1))) {
        className = className;
      } else {
        // cut of the method name
        className = className.substring(0, className.lastIndexOf("."));
      }
    }
    if (qname.endsWith("INIT")) {
      className = className.substring(0, className.lastIndexOf("."));
    }

    // FIXME: case if qname is a field

    return className;
  }

  public static SootMethod findSootMethod(
      SootClass sootClass, String methodName, ScopeType scopeType) {
    List<String> parameterList = null;
    String retType = null;
    methodName = methodName.substring(methodName.lastIndexOf(".") + 1);

    if (methodName.contains("(")) {

      // get the method based on the qname
      parameterList =
          Arrays.stream(
                  methodName
                      .substring(methodName.indexOf("(") + 1, methodName.indexOf(")"))
                      .split(","))
              .filter(x -> !StringUtils.isBlank(x))
              .collect(Collectors.toList());

      methodName = methodName.substring(0, methodName.indexOf("("));

      // check if the method is a constructor
      if (Character.isUpperCase(methodName.charAt(0))) {
        methodName = "<init>";
      } else if (scopeType == ScopeType.INIT) {
        methodName = "<init>";
      } else if (scopeType == ScopeType.CLINIT) {
        methodName = "<clinit>";
      }
    }
    // check if the methodname contains the return type
    if (methodName.contains(" ")) {
      String[] split = methodName.split(" ");
      if (split.length < 2) {
        // opps something is strange or we still have modifiler, e.g. public final .....
        throw new IllegalArgumentException("Cannot construct SootIdentifier for " + methodName);
      }
      // the method name is the last element
      methodName = split[split.length - 1];

      // the return type is the second last??
      // FIXME is this always correct
      retType = split[split.length - 2];
    }
    // a inner constructor get's the outer class as a parameter
    if (sootClass.isInnerClass() && scopeType == ScopeType.INIT) {
      InnerClassTag innerClassTag = null;
      for (Tag tag : sootClass.getTags()) {
        if (tag instanceof InnerClassTag) {
          if (((InnerClassTag) tag)
              .getInnerClass()
              .replaceAll("/", ".")
              .equals(sootClass.getName())) {
            innerClassTag = (InnerClassTag) tag;
            break;
          }
        }
      }
      if (innerClassTag != null) {
        if (!Modifier.isStatic(innerClassTag.getAccessFlags())) {
          // only non static classes take an instance of the outer class as an argument
          SootClass outerClass = sootClass.getOuterClass();
          parameterList.add(outerClass.getType().toQuotedString());
        }
      }
    }

    SootMethod referenceMethod = null;
    for (SootMethod sootMethod : sootClass.getMethods()) {
      if (sootMethod.getName().equals(methodName)) {
        List<Type> parameterTypes = sootMethod.getParameterTypes();
        // check if parameter name matches
        if (parameterTypes.size() == parameterList.size()) {

          for (int i = 0; i < parameterList.size(); i++) {
            if (!parameterTypes.get(i).getEscapedName().contains(parameterList.get(i))) {
              // go on
              break;
            }
          }
          referenceMethod = sootMethod;
          break;
        }
      }
    }
    return referenceMethod;
  }

  public static String toFileName(String fqnClassName) {
    if (fqnClassName.contains("(") || fqnClassName.contains("<")) {
      // we have a field or method but not a classname
      throw new IllegalArgumentException(fqnClassName + " is not a valid FQN Classname");
    }

    String fileName = fqnClassName.replace(".", File.separator);
    fileName = fileName + ".class";
    return fileName;
  }
}
