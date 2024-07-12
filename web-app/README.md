# food4future Study Management Portal

## Introduction

This repository contains the source code of the Study Management Portal, which is part of the food4future study management system.

The Study Management Portal fulfills the following tasks:
- Providing a web interface for the study staff to manage the studies and to access the acquired study data
- Food Image, Entry and Questionnaire Management
- Management of all studies, study contents, settings and staff members

This is a Client-side rendered [React.js](https://react.dev/) Web application, which communicates with the backend server via a RESTful API. The backend server also takes care of serving the static files of the web application to the client web browser.

[Vite](https://vitejs.dev/) is used as the build tool and dev environment.

## Build via Docker

It is **strongly** recommended to use the provided Docker setup to run the whole f4f Study Management Services including this web application. This ensures that all required dependencies are installed and configured correctly.

See the [Main Readme](../Readme.md) for more details about the installation.

## Manual Build

To manually build the web application, you need a working [Node.js](https://nodejs.org/) environment with [yarn](https://yarnpkg.com/) installed. *npm* should work to, but the third-party licenses information might not be properly generated.

Clone this repository to your local machine and run the following commands in the root directory of the repository:

```bash
$ yarn global install vite
$ yarn install
$ yarn run build
```


This will create a new directory `dist` containing the built web application. You can then set the full path to the `dist` directory in the `web_app_path` configuration setting in the `config.json` of the **backend server**.

## Access the web application

When the backend server is properly configured and running, you will be able to access the **f4f Study Management Portal** by opening the URL of the server in your web browser:

    https://{YOUR_SERVER_URL}[:{YOUR_SERVER_PORT}]/manage

## Legal Note and License

This software was developed by the [Research Group Telematics](https://en.th-wildau.de/research-transfer/research/telematics/) at the [Technical University of Applied Sciences Wildau](https://en.th-wildau.de/).

The development was part of the research project "Agrarsysteme der Zukunft: [food4future – Nahrung der Zukunft](https://food4future.de/)" funded by the German Federal Ministry of Education and Research (BMBF) under the funding code **031B0730A**. Funding is provided by the BMBF as part of the "[Agrarsysteme der Zukunft](https://agrarsysteme-der-zukunft.de/)" funding program.


The source code of the f4f Study Companion Android App is licensed under the [GPLv2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html) license. See the [LICENSE](./LICENSE) file for more details.

<img src="https://en.th-wildau.de/files/_processed_/b/7/csm_BMBF_gefoerdert_2017_en_3164c4e794.jpg" width="250" />
