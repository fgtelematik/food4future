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

import {useSessionState} from "../hooks/useState/useSessionState.ts";

export const UnsupportedRole = () => {
    const currentUser = useSessionState(state => state.user);

    return (
        <div>
            <h1>Unsupported Role</h1>
            <p>Sorry...<br/>With the role <b>{currentUser?.role ?? "Unknown"}</b> you cannot use this web application.</p>
            <p>Please use the Android App for accessing your study.</p>
        </div>
    );
};