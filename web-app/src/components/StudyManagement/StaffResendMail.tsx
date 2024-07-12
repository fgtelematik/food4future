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

import {ResendMailRequest, Role, TestUsernameResult, User, UserInput} from "../../models/auth.ts";
import {FC, useEffect, useState} from "react";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import {CircularProgress, debounce, FormControlLabel, FormGroup, Switch, TextField} from "@mui/material";
import useApi from "../../hooks/useApi";
import {AutocompleteEditField} from "../Editor/AutocompleteEditField.tsx";
import CheckIcon from '@mui/icons-material/Check';
import {isValidEmailAddress, locStr} from "../../utils.ts";
import {useDataState} from "../../hooks/useState/useData.ts";
import {ObjectId} from "../../models/main.ts";

export interface StaffResendMailProps {
    user: User | null;
    setUser: (user: User | null) => void;
    onSubmit: (user: User, resendMailRequest: ResendMailRequest) => Promise<void>;
}

export const StaffResendMail: FC<StaffResendMailProps> = ({onSubmit, user, setUser}) => {
    const initialRequest: ResendMailRequest = {email: "", reset_password: false};

    const [request, setRequest] = useState<ResendMailRequest>(initialRequest);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const isValidEmail = isValidEmailAddress(request.email)

    useEffect(() => {
        if (!!user) {
            setIsSubmitting(false);
            setRequest(initialRequest);
        }
    }, [user]);

    const onRequestSubmit = async () => {
        if (!user?.id)
            return;

        setIsSubmitting(true)
        await onSubmit(user, request);
        setIsSubmitting(false)
        
        setUser(null);
    }


    return <EditorDialog
        open={!!user}
        onSubmit={onRequestSubmit}
        onCancel={() => setUser(null)}
        isValidContent={isValidEmail && !isSubmitting}
        title={<>Re-send registration email for <b>{user?.username ?? "(unknown user)"}</b></>}>

        <p>The staff member's credentials (including a login QR code) can be (re-)sent to an email address specified
            here.</p>
        <p>
            A previously sent password cannot be sent again. If the staff member has forgotten their password, please
            please activate the <b>Reset Password</b> switch to auto-generate a new password, which will then be
            included in the
            email.
        </p>
        <p>The email address will not be stored and only be used for this purpose.</p>
        <TextField
            disabled={isSubmitting}
            value={request.email}
            autoFocus={true}
            onChange={e => setRequest({...request, email: e.target.value})}
            label={"E-Mail Address"}
            type={"email"}
            placeholder={"staff@" + window.location.hostname}
            error={!isValidEmail}
            helperText={isValidEmail ?
                "" :
                <>Please enter a valid email address or leave empty.<br/></>
            }
        />

        <FormGroup>
            <FormControlLabel control={
                <Switch
                    disabled={isSubmitting}
                    checked={request.reset_password}
                    onChange={(_, reset_password) =>
                        setRequest({...request, reset_password})}
                />
            } label="Reset Password"/>
            <p>Activating will <b>reset</b> the user's password and autogenerate a new.</p>
        </FormGroup>

        {isSubmitting &&
            <div className={"simple-row"} style={{justifyContent: "center"}}><CircularProgress size={"1em"}/>
                <p>Processing request...</p>
            </div>
        }

    </EditorDialog>
}