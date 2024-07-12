# food4future Study Management Backend Server

## Introduction
This repository contains the source code of the backend server, which is part of the food4future study management system.

The Backend Server fulfills the following tasks:
- Storage and management of all study data, encapsulating access of the MongoDB database
- Syncing the data with the Study Companion App
- Providing a RESTful API to be accessed by the Study Management Portal and the Study Companion App and also by scientists to access the acquired study data
- provide access to the APK file of the Study Companion App and to the Study Management Portal web application

## Installation via Docker

It is **strongly** recommended to use the provided Docker setup to to build and run all f4f Study Management Services including this backend server. This ensures that all required dependencies are installed and configured correctly.

See the [Main Readme](../Readme.md) for more details about the installation.

## Native manual Installation

### Requirements

If you want to install the backend server natively on the server (which is, I repeat, not receommended), you need to fulfill the following requirements:

- Ubuntu 18.04 or later (might work on other Linux distributions, but this is not tested)
- A valid DNS hostname reachable from the internet, properly configured firewall and abilitiy to allow specific to open ports on the server
- Installed nginx web server
- Python 3.6 or later
- MongoDB database instance which is reachable from the server
- A valid SSL certificate for the server (e.g. from [Let's Encrypt](https://letsencrypt.org/))
- An accessible SMTP email server allowing the server to send emails. The server must support TLS or STARTTLS and must be reachable from the server.
- A built version of the Study Management Portal web application (optional)
- A built version of the Study Companion Android App (optional)

### Installation

#### 1. Clone this repository to your server (see [here](https://docs.github.com/en/github/creating-cloning-and-archiving-repositories/cloning-a-repository) for instructions)

#### 2. Place the SSL certificate at /etc/ssl/certs/f4fserver.pem and the private key at /etc/ssl/private/f4fserver.key (or adjust the paths in the nginx configuration file at scripts/f4fserver-nginx.conf)


#### 3. Run the setup script to install all required dependencies and to configure the server:

```bash
$ sudo scripts/setup.sh -p {PUBLIC_PORT} -l {LOCAL_PORT} -s {INSTANCE_SUFFIX}
```

Replace `{PUBLIC_PORT}` with the port, which should be used to access the server from the public internet. Default: 8443.

Replace `{LOCAL_PORT}` with the port, which is used locally and nginx forwards to as Reverse Proxy. This port should NOT be publicly accessible. Default: 8000.

. Replace `{INSTANCE_SUFFIX}` with a unique suffix for the server instance (e.g. `dev` or `prod`). This allows you to run multiple instances of the server backend. Default: None 

*Note:* The script will create a new user `f4f` and install [pipenv](https://pipenv.pypa.io/) and [PyEnv](https://github.com/pyenv/pyenv) in this user's environment!

Both a new nginx site and a new systemd service will also be created. The service will either be named `f4fserver` or `f4fserver-{INSTANCE_SUFFIX}` if the instance suffix is set.


#### 4. Adjust the configuration

Open the configuration file `config.json` and adjust the settings to your needs. 

Most important:
-  define the access information (host, port, credentials, database, auth source) for the MongoDB database. Optionally you can enable SSH tunneling for accessing the database.
-  set the initial manager credentials (`reset_manager_username` and `reset_manager_password` parameters) 
-  SMTP E-Mail server settings.
-  Optionally set the path to the `dist` dir of the f4f Study Management Portal web application (`web_app_path`) and the path of the Study Companion Android App (`apk_file`)

Please note: The setting `public_base_url` and all `ssl_` settings are deprecated and shoud not be changed! 
The Public URL is internally determined from the `X-Forwarder-`-HTTP-Headers and TLS encryption is handled by the nginx server.

#### 5. Start the server

```bash
$ sudo systemctl start f4fserver[-{INSTANCE_SUFFIX}]
```

#### 6. Enable the server to start on boot

```bash
$ sudo systemctl enable f4fserver[-{INSTANCE_SUFFIX}]
```



### Uninstall

To uninstall the server including the nginx site and the systemd service, run the following command:

```bash
$ sudo scripts/uninstall.sh -s {INSTANCE_SUFFIX}
```

Make absolutely sure you replace `{INSTANCE_SUFFIX}` with the correct suffix of the server instance (if you defined one). Otherwise, the script can remove the wrong files and directories!

Please note, that the script will not remove the user `f4f` and the installed pipenv and pyenv. You have to remove them manually if you don't need them anymore.

## Legal Note and License

This software was developed by the [Research Group Telematics](https://en.th-wildau.de/research-transfer/research/telematics/) at the [Technical University of Applied Sciences Wildau](https://en.th-wildau.de/).

The development was part of the research project "Agrarsysteme der Zukunft: [food4future – Nahrung der Zukunft](https://food4future.de/)" funded by the German Federal Ministry of Education and Research (BMBF) under the funding code **031B0730A**. Funding is provided by the BMBF as part of the "[Agrarsysteme der Zukunft](https://agrarsysteme-der-zukunft.de/)" funding program.


The source code of the f4f Study Companion Android App is licensed under the [GPLv2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html) license. See the [LICENSE](./LICENSE) file for more details.

<img src="https://en.th-wildau.de/files/_processed_/b/7/csm_BMBF_gefoerdert_2017_en_3164c4e794.jpg" width="250" />
