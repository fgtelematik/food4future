#  f4f Study Companion Backend Server
#
#  Copyright (c) 2024 Technical University of Applied Sciences Wildau
#  Author: Philipp Wagner, Research Group Telematics
#  Contact: fgtelematik@th-wildau.de
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation version 2.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program; if not, write to the Free Software
#  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

import os
import subprocess

command = 'pip-licesnses --format=plain-vertical --with-license-file --no-license-path'
file_path = 'licenses-disclaimer.txt'


def generate_license_info():
    try:
        # Execute the command and capture the output
        output = subprocess.check_output(command, shell=True, text=True)

        # Write the output to the file, replacing the old content
        with open(file_path, 'w', encoding='utf-8') as file:
            file.write(output)

        print(f'License disclaimer info generated and stored in {file_path}')
    except subprocess.CalledProcessError as e:
        print(f'Error running command: {command}.\n\tCould not update license info. Please check file permissions.')


def get_license_info():
    if not os.path.exists(file_path):
        generate_license_info()

    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            return file.read()
    except Exception as e:
        print(f'Error reading license info: {e}')
        return "Backend Server license info file could not be generated. Please check file permissions on the server."
