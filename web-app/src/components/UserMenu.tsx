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

import {Button} from "@mui/material";
import useApi from "../hooks/useApi";
import {useSessionState} from "../hooks/useState/useSessionState.ts";
import {RoleLabel} from "../models/auth.ts";

export const UserMenu = () => {
    const currentUser = useSessionState(state => state.user);
    
    if(!currentUser)
        return null;
    
    const onLogoutButtonClicked = async () => {
        await useApi().logout();
    }
    
    return (
        <div className={"user-menu"}>
            <p>{currentUser.username}&nbsp;<span className={"additional-info"}>| {RoleLabel[currentUser.role]}</span></p>
            <Button onClick={onLogoutButtonClicked}>Logout</Button>
        </div>
    )
}