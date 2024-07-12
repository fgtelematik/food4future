/*
 * f4f Study Management Portal (Web App)
 *
 * Copyright (c) 2024 Technical University of Applied Sciences Wildau
 * Author: Philipp Wagner, Research Group Telematics
 * Contact: fgtelematik@th-wildau.de
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation version 2.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import { execSync } from 'child_process';
import { writeFileSync } from 'fs';

// Command to run
const command = 'yarn licenses generate-disclaimer';

try {
    // Execute the command and capture the output
    const output = execSync(command, { encoding: 'utf-8' });

    // Specify the file path to store the output
    const filePath = './public/licenses-disclaimer.txt';

    // Write the output to the file, replacing the old content
    writeFileSync(filePath, output);

    console.log(`Update licenses info stored in: ${filePath}`);
} catch (error) {
    console.warn(`WARNING: Could not execute: ${command}.\n\tError: ${error.message}\n\tThe licenses info will not be updated.`);
}
