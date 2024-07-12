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

import {Permission, PermissionType} from "../../models/form.ts";
import {Card, FormControl, FormControlLabel, Radio, RadioGroup} from "@mui/material";
import {useEffect} from "react";
import {usePersistentState} from "../../hooks/useState/usePersistentState.ts";
import {Role} from "../../models/auth.ts";

enum PermissionTypeInternal {
    None = "None",
    Read = "Read",
    ReadWrite = "ReadWrite",
}

const roleLabels : {
    // [key in keyof Pick<typeof Role, 'Nurse' |'Participant'>]: string  // typing approach not working
    [key in Role]: string
} = {
    // We only support customizable permissions for nurses and participants.
    // Currently Supervisor (Scientist) has always full read-only access.
    [Role.Nurse]: "Staff",
    [Role.Participant]: "Participant",
    [Role.Supervisor]: "-- (unused value to satisfy typescript compiler)",      // workaround
    [Role.Administrator]: "-- (unused value to satisfy typescript compiler)"    // workaround
}

export const InputPermissionsField = (props: {
    permissions?: Permission[],
    setPermissions: (newPermissions: Permission[]) => void,
}) => {

    const lastPermissions = usePersistentState(state => state.lastPermissions);
    const setLastPermissions = (permissions: Permission[]) => usePersistentState.setState({lastPermissions: permissions});

    useEffect(() => {
        if (!props.permissions)
            // apply last manually set permission (or default permissions) as default value for new fields.
            props.setPermissions(lastPermissions);
    }, [props.permissions]);

    const onChange = (role: Role, type: PermissionTypeInternal) => {
        if (!props.permissions)
            return;

        const newPermissions = props.permissions.filter(p => p.role != role);
        if (type != PermissionTypeInternal.None)
            newPermissions.push({
                role: role,
                type: type == PermissionTypeInternal.Read ? PermissionType.Read : PermissionType.Edit
            });

        props.setPermissions(newPermissions);
        setLastPermissions(newPermissions);
    }

    const getInternalType = (role: Role): PermissionTypeInternal => {
        if (!props.permissions)
            return PermissionTypeInternal.None;

        const permission = props.permissions.find(p => p.role == role);
        if (!permission)
            return PermissionTypeInternal.None;

        return permission.type == PermissionType.Read ? PermissionTypeInternal.Read : PermissionTypeInternal.ReadWrite;
    }

    return (
        <Card
            className={"permissions-card"}
            title={"Edit Field Permissions"}
        >
            <h2>Field Permissions</h2>
            <table className={"permissions-table"}>
                <tbody>
                {[Role.Nurse, Role.Participant].map(role => (
                    <tr key={role.toString()}>
                        <th>{roleLabels[role]}:</th>
                        <td>
                            <FormControl>
                                <RadioGroup
                                    value={getInternalType(role)}
                                    onChange={e => onChange(role, e.target.value as PermissionTypeInternal)}
                                    row>
                                    <FormControlLabel value={PermissionTypeInternal.ReadWrite} control={<Radio/>}
                                                      label="Read & Edit"/>
                                    <FormControlLabel value={PermissionTypeInternal.Read} control={<Radio/>}
                                                      label="Read only"/>
                                    <FormControlLabel value={PermissionTypeInternal.None} control={<Radio/>}
                                                      label="No Access"/>

                                </RadioGroup>
                            </FormControl>
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>
        </Card>
    );
};