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

import {create} from "zustand";
import {User} from "../../models/auth.ts";
import {AlertDialogProps} from "../../components/AlertDialog.tsx";

import {Study} from "../../models/study.ts";

type SessionState = {
    user: User | null;
    alertDialog: AlertDialogProps | null;
    globalError: string | null;
    
    
    currentEditStudy: Study | undefined;
    setCurrentEditStudy(study: Study | undefined): void;
    editStudySectionErrors: {[key: string]: boolean};
    setEditStudySectionErrors(errors: {[key: string]: boolean}): void;
    
}

export const useSessionState = create<SessionState>()(
    (set, get) => ({
        user: null,
        alertDialog: null,
        globalError: null,
        currentEditStudy: undefined,
        setCurrentEditStudy: (study) => set(() => ({currentEditStudy: study})),
        editStudySectionErrors: {},
        setEditStudySectionErrors: (errors) => set(() => ({editStudySectionErrors: errors})),
    }));
