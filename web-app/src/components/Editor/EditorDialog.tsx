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

import React, {FC} from "react";
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle} from "@mui/material";
import './EditorDialog.css'

export type EditorDialogProps = {
    open: boolean;
    children: React.ReactNode;
    onSubmit: () => void;
    onCancel?: () => void;
    title?: React.ReactNode;
    infoText?: string;
    isValidContent?: boolean;
}


export const EditorDialog: FC<EditorDialogProps> = ({open, title, infoText, children, onCancel, onSubmit, isValidContent}) => {
    return (
        <Dialog
            disableRestoreFocus={true}
            open={open}
            PaperProps={{style: {width: "100%"}}}
            fullWidth={true}
            onClose={(_, trigger) => { if(trigger=="escapeKeyDown") onCancel && onCancel(); }}>
            {title && <DialogTitle>{title}</DialogTitle> }
            {infoText && <DialogContentText>{infoText}</DialogContentText> }
            <DialogContent>
                <DialogContentText>
                </DialogContentText>
                <div className={"editor-dialog-form"}>
                    {children}
                </div>
            </DialogContent>
            <DialogActions>
                {onCancel && <Button onClick={() => onCancel()}>Cancel</Button>}
                <Button disabled={isValidContent !== undefined && !isValidContent}
                        onClick={() => onSubmit && onSubmit()}>Save</Button>
            </DialogActions>
        </Dialog>
    )
}


/**
 * <Button variant={"contained"} onClick={onCancel}>Cancel</Button>
 *                 <Button variant={"contained"} onClick={onSubmit} disabled={isValidContent !== undefined && !isValidContent}>Submit</Button>
 *
 */