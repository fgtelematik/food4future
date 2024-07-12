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

import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import {FC, ReactElement, ReactNode} from "react";
import {useSessionState} from "../hooks/useState/useSessionState.ts";

export type AlertDialogProps = {
    onConfirm: () => void;
    onCancel?: () => void;
    title?: string | ReactElement
    content?: string | ReactElement
    confirmText?: string;
    cancelText?: string;
    confirmDisabled?: boolean;
    children?: ReactNode;
    onClose?: () => void;
}

const AlertDialog: FC<AlertDialogProps> = (props) => {
    const closeDialog = props.onClose ? 
        () => props.onClose!() : () => useSessionState.setState({alertDialog: null});
    
    if(!props)
        return null;

    const handleClose = (confirm : boolean) => {
        if(confirm)
            props.onConfirm();
        else
            props.onCancel && props.onCancel();
        
        closeDialog();
    };

    return (
        <Dialog
            open={true}
            disableRestoreFocus
            onClose={() => handleClose(false)}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            {props.title && <DialogTitle id="alert-dialog-title">
                {props.title}
            </DialogTitle>}
            <DialogContent>
                <DialogContentText id="alert-dialog-description">
                    {props.content}
                    {props.children}
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                {props.cancelText && <Button onClick={() => handleClose(false)}>{props.cancelText}</Button>}
                <Button disabled={props.confirmDisabled} onClick={() => handleClose(true)}>
                    {props.confirmText}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

export default AlertDialog;
