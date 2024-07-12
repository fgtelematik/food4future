# food4future Study Companion Android App

## Introduction

This repository contains the source code of the f4f Study Companion Android App, which is part of the food4future Study Management system.

The Study Companion App fulfills the following tasks:
- Guiding the study participants through the study process
- Collecting the study data (nutrition input, questionnaire and sensor data) and sending it to the backend server
- Providing an individual user interface for the study participants to access their study data
- Providing a user interface for the study staff to manage the study participants and to set or access the participant's profile data

This is a native Android Application written in Java, which communicates with the backend server via a RESTful API.

## Build via Docker

It is **strongly** recommended to use the provided Docker setup to build all f4f Study Management Services including the Android App. This ensures that all required dependencies are installed and configured correctly.

See the [Main Readme](../Readme.md) for more details about the installation.

## Build via Android Studio

To manually build the Android App, you need a working [Android Studio](https://developer.android.com/studio) environment with the Android SDK and the Android NDK installed.

Clone this repository and open the root directory in Android Studio. Android Studio will sync with Gradle and automatically download the required dependencies. After that, you can build the app by clicking "Build -> Build Bundle(s) / APK(s) -> Build APK(s)" in the top menu.

The app will be built as an APK file, and Android Studio should tell you where to locate it. You can then store the  file on the **Backend Server**. In the f4f Backend Server, you can then set the full path to the APK file in the `apk_file` configuration setting in the `config.json` of the **backend server**.

## Legal Note and License

This software was developed by the [Research Group Telematics](https://en.th-wildau.de/research-transfer/research/telematics/) at the [Technical University of Applied Sciences Wildau](https://en.th-wildau.de/).

The development was part of the research project "Agrarsysteme der Zukunft: [food4future – Nahrung der Zukunft](https://food4future.de/)" funded by the German Federal Ministry of Education and Research (BMBF) under the funding code **031B0730A**. Funding is provided by the BMBF as part of the "[Agrarsysteme der Zukunft](https://agrarsysteme-der-zukunft.de/)" funding program.


The source code of the f4f Study Companion Android App is licensed under the [GPLv2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html) license. See the [LICENSE](./LICENSE) file for more details.

<img src="https://en.th-wildau.de/files/_processed_/b/7/csm_BMBF_gefoerdert_2017_en_3164c4e794.jpg" width="250" />


