@echo off
cd /D "%~dp0\.."

echo Installing required packages ...
pipenv install

IF NOT EXIST config.json (
	echo Create default config.js
	copy schema\default_config.json config.json
)

echo Setup complete.
pause