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

export class ApiRequestError extends Error {
    statusCode: number;
    response: Response;
    content?: Promise<any>

    constructor(response: Response) {
        super(`API Request Error. HTTP status: ${response.status}`);
        this.name = 'ApiRequestError';
        this.response = response;
        this.statusCode = response.status;
        this.content = response.json();

        Object.setPrototypeOf(this, new.target.prototype); // restore prototype chain

        console.log("Api Request Error (Status Code: " + response.status + ")");

        response.json()?.then((json) => {
            console.log("Error JSON Response: ", json);
            this.content = json;
        }).catch(() => {});
    }
}

