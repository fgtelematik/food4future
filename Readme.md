# food4future Study Management Services

## Introduction
This repository contains the source code of all backend and frontend services for the food4future study management system.

This includes:
 - the **f4f Study Management Portal** web application,
 - the **f4f Study Companion** Android App,
 - the Study Management [**Backend Server**](./backend/Readme.md) (used for communication by the web app and the Android app)
  providing 
  - a **RESTful API** for allowing scientists to access the acquired study data

## Requirements
- A UNIX-based server, which is reachable from the public internet with valid hostname and at least one publicly reachable inbound port (e.g. 80 or 443)
- [Docker](https://www.docker.com/) with *Docker Compose* installed on the server
- An accessible SMTP server allowing the server to send emails. The server must support TLS or STARTTLS and must be reachable from the server.
- For encrypted communication between frontend, app and server, a valid SSL/TLS certificate and private key file is required (e.g. from [Let's Encrypt](https://letsencrypt.org/))

Thtat's it! Since everything is docker-based, no build tools or other dependencies are required.

## Install and Run

#### 1. Clone this repository to your server (see [here](https://docs.github.com/en/github/creating-cloning-and-archiving-repositories/cloning-a-repository) for instructions) including all submodules:

```bash
$ git clone --recurse-submodules {URL_TO_THIS_REPOSITORY}
```

#### 2. Open the configuration file `.env.default` and set all the server configuration parameters (see the comments in this file for more details) and most important define the initial manager credentials (`RESET_MANAGER_USERNAME` and `RESET_MANAGER_PASSWORD` parameters) and the SMTP email server settings.
   
#### 3. Save the file as `.env` (without the `.default` extension)

#### 4. Run `docker-compose up -d` to start all the f4f services.
If you run this command the first time, this will first download AND build
all the required components. This may take a while, but only needs to be done once.
For all subsequent runs, only the containers will be started.

## Legal Note and License

This software was developed by the [Research Group Telematics](https://en.th-wildau.de/research-transfer/research/telematics/) at the [Technical University of Applied Sciences Wildau](https://en.th-wildau.de/).

The development was part of the research project "Agrarsysteme der Zukunft: [food4future – Nahrung der Zukunft](https://food4future.de/)" funded by the German Federal Ministry of Education and Research (BMBF) under the funding code **031B0730A**. Funding is provided by the BMBF as part of the "[Agrarsysteme der Zukunft](https://agrarsysteme-der-zukunft.de/)" funding program.


The source code of the f4f Study Companion Android App is licensed under the [GPLv2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html) license. See the [LICENSE](./LICENSE) file for more details.

<img src="https://en.th-wildau.de/files/_processed_/b/7/csm_BMBF_gefoerdert_2017_en_3164c4e794.jpg" width="250" />
