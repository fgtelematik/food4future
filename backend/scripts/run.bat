@echo off
cd /D "%~dp0\.."

echo Starting f4f backend server...
pipenv run python main.py
