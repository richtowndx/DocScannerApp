#!/bin/sh

APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${0%/*}" > /dev/null && pwd )

# Use JAVA_HOME if set
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" -classpath "$CLASSPATH" \
    -Dorg.gradle.appname=$APP_BASE_NAME \
    org.gradle.wrapper.GradleWrapperMain "$@"
