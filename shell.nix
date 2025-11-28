{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  # Tools needed for Kotlin/Gradle builds and ANTLR grammar generation.
  packages = with pkgs; [
    jdk21
    gradle
    kotlin
    antlr4
  ];

  shellHook = ''
    export JAVA_HOME=${pkgs.jdk21.home}

    if [ -n "$GRADLE_OPTS" ]; then
      export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME $GRADLE_OPTS"
    else
      export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME"
    fi
  '';
}
