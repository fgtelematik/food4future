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

import {FC, useEffect, useState} from "react";
import {StudyManagementSectionProps} from "./interface.ts";
import {
    Alert,
    Avatar,
    Button,
    CircularProgress,
    IconButton,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    Tooltip
} from "@mui/material";
import EmailIcon from '@mui/icons-material/Email';
import EditIcon from '@mui/icons-material/Edit';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import BiotechIcon from '@mui/icons-material/Biotech';
import SchoolIcon from '@mui/icons-material/School';
import PersonIcon from '@mui/icons-material/Person';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import {ResendMailRequest, Role, User, UserInput} from "../../models/auth.ts";
import {NEW_ELEMENT_ID} from "../../hooks/useState/useData.ts";
import useApi from "../../hooks/useApi";
import {StaffEditor} from "./StaffEditor.tsx";
import {useSessionState} from "../../hooks/useState/useSessionState.ts";
import {StaffResendMail} from "./StaffResendMail.tsx";


interface StaffEntryProps {
    user: User;
    onDelete: (user: User) => void;
    onEdit: (user: User) => void;
    onMail: (user: User) => void;
}

const roleIcons = {
    [Role.Nurse]: <BiotechIcon/>,
    [Role.Supervisor]: <SchoolIcon/>,
    [Role.Administrator]: <AdminPanelSettingsIcon/>,
    [Role.Participant]: <PersonIcon/>
}

const StaffItem : FC<StaffEntryProps> = ({user, onDelete, onEdit, onMail}) => {
    return (
        <ListItem
            secondaryAction={
                <div className={"action-buttons"}>
                    <Tooltip title={"Edit Profile"}>
                    <IconButton edge="end" aria-label="edit" onClick={() => onEdit(user)}><EditIcon/></IconButton>
                    </Tooltip>
                    
                    <Tooltip title={"Re-Send Registration Mail"}>
                    <IconButton edge="end" aria-label="mail" onClick={() => onMail(user)}><EmailIcon/></IconButton>
                    </Tooltip>
                        
                    <Tooltip title={"Delete Profile"}>
                    <IconButton edge="end" aria-label="delete" onClick={() => onDelete(user)}><PersonRemoveIcon /></IconButton>
                    </Tooltip>
                </div>
            }
        >
            <ListItemAvatar>
                <Avatar>
                    {roleIcons[user.role]}
                </Avatar>
            </ListItemAvatar>
            <ListItemText
                primary={user.username}
                secondary={user.role === Role.Supervisor ? "Scientist" : user.role}
            />
        </ListItem>
    );
}

export const StaffSection : FC<StudyManagementSectionProps> = ({study}) => {
    const [staffList, setStaffList] = useState<User[]>([
        {username: "Test-Scientist", role: Role.Supervisor}, // Test entries
        {username: "Test-Nurse", role: Role.Nurse}
    ])
    
    const [editUser, setEditUser] = useState<UserInput | null>(null);
    const [resendMailUser, setResendMailUser] = useState<User | null>(null);
    
    const [loading, setLoading] = useState<boolean>(false);
    const fetchStaff = async () => {
        setLoading(true);
        const staff = await useApi().fetchStaff(study.id);
        setLoading(false);
        setStaffList(staff);
    }
    
    useEffect(() => {
        fetchStaff();
    }, [study]);
    
    const onEditUser = (user?: User) => {
        if(!user)
            // add new user
            setEditUser({study_id: study.id, username: "", role: Role.Nurse});
        else    
            // edit user
            setEditUser(user);
    }
    
    const onSaveUser = async (editUser: UserInput) => {
        try {
            await useApi().upsertUser(editUser);
            await fetchStaff();
            return true;
        } catch (e) {
            console.error(e);
        }
        
        return false;
    }
    
    const onDeleteUser = async (user: User) => {
        if (!user.id)
            return; // should not happen

        const roleName = user.role === Role.Supervisor ? "Scientist" : user.role;

        useSessionState.setState({
            alertDialog: {
                content: <>Delete {roleName} <br/><b>{user.username}</b> ?</>,
                onConfirm: async () => {
                    await useApi().deleteUser(user.id!);
                    await fetchStaff();
                },
                onCancel: () => {
                },
                confirmText: "Yes",
                cancelText: "No"
            }
        });
    }
    
    if(study.id == NEW_ELEMENT_ID) {
        return (
            <div className={"staff-section"}>
                <p>
                    <Alert severity="info">Please save the study first to add staff members.</Alert>
                </p>
            </div>
        );
    }

    if(loading) {
        return (
            <div className={"staff-section"}>
                <p><CircularProgress/><br/>Loading staff members...</p>
                    <p>&nbsp;</p>
            </div>
        );
    }

    const onResendMail = (user: User) => {
        setResendMailUser(user);
    }
    
    const onSubmitResendMail = async (user:User, resendMailRequest:ResendMailRequest) => {
        await useApi().resendRegistrationMail(user.id!, resendMailRequest);

        useSessionState.setState({
            alertDialog: {
                content: 
                    (resendMailRequest.reset_password ? 
                    "The password has been reset and the" : "The") + 
                    " registration mail has been successfully sent.",
                onConfirm: async () => {},
                confirmText: "OK"
            }
        });
    };
    
    return (
        <div className={"staff-section"}>
            { staffList.length == 0 &&
                <p>
                    No staff profiles are yet created.
                    <br/>Please click the button below to add a new <b>nurse</b> or <b>scientist</b>.
                </p>
            }


            {   staffList.length > 0 &&
                <List dense={true}>
                    {
                        staffList.map(user => <StaffItem
                            key={user.username}
                            user={user}
                            onDelete={() => onDeleteUser(user)}
                            onEdit={() => onEditUser(user)}
                            onMail={() => onResendMail(user)}
                        />)
                    }
            </List>
            }
            <div>
                <Button
                    variant="outlined"
                    startIcon={<PersonAddIcon/>}
                    onClick={() => onEditUser()}
                >Add Staff member...</Button>
            </div>
            <StaffEditor editUser={editUser} setEditUser={setEditUser} onSaveUser={onSaveUser}/>
            <StaffResendMail user={resendMailUser} setUser={setResendMailUser} onSubmit={onSubmitResendMail}/>
        </div>
    );
};