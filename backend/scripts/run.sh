#!/bin/bash
#
# f4f Study Companion Backend Server
#
# Copyright (c) 2024 Technical University of Applied Sciences Wildau
# Author: Philipp Wagner, Research Group Telematics
# Contact: fgtelematik@th-wildau.de
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation version 2.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

cd "$(dirname "$0")/.."

# Get the current user
current_user=$(whoami)

# Check if the current user is "f4f"
if [[ "$current_user" != "f4f" ]]; then
  echo "Error: You must be 'f4f' to run this script."
  exit 1
fi

echo Starting f4f backend server...
export PIPENV_PYTHON="$(/home/f4f/.pyenv/bin/pyenv root)/shims/python"
pipenv run python main.py
