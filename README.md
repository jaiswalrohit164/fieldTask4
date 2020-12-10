# Field Task

Field Task is an Android app that can be used for collecting data for Monitoring and Evaluation from outside the office including locations without data connectivity. It also has task management functionality which enables it to be used for work force management applications.

Field Task is a fork of [odkCollect](http://opendatakit.org/use/collect/). 
 

## Table of Contents
* [Dependencies](#dependencies)
* [Setting up your development environment](#setting-up-your-development-environment)

## Dependencies
Most dependencies are managed using gradle, however some Field Task functionality requires a modified version of JavaRosa(https://github.com/smap-consulting/javarosa).  You will need to download this to your development environment in order to access these features.

## Setting up your development environment

1. Download and install [Git](https://git-scm.com/downloads) and add it to your PATH

1. Download and install [Android Studio](https://developer.android.com/studio/index.html) 

1. Fork the Field Task project ([why and how to fork](https://help.github.com/articles/fork-a-repo/))

1. Clone your fork of the project locally. At the command line:

        git clone https://github.com/YOUR-GITHUB-USERNAME/fieldTask4

1. Use Android Studio to import the project from its Gradle settings. To run the project, click on the green arrow at the top of the screen.

1. Windows developers: continue configuring Android Studio with the steps in this document: [Developing ODK Collect on Windows](docs/WindowsDevSetup.md).

1. Make sure you can run unit tests by running everything under `collect_app/src/test/java` in Android Studio or on the command line:

    ```
    ./gradlew testDebug
    ```

1. Make sure you can run instrumented tests by running everything under `collect_app/src/androidTest/java` in Android Studio or on the command line:

    ```
    ./gradlew connectedAndroidTest
    ```


Acknowledgements
----------------

This project includes:
* the odkCollect Library of (http://opendatakit.org/)
* the Android SDK from [MapBox] (https://www.mapbox.com/)
