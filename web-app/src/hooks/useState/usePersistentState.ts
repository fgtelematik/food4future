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
import {persist} from "zustand/middleware";
import {ObjectId} from "../../models/main.ts";
import {Permission, PermissionType} from "../../models/form.ts";
import {Role} from "../../models/auth.ts";

type PersistentState = {
    devApiUrl: string;
    getApiUrl(): string;
    getFrontendBaseUrl(): string;
    apiTimeout: number;
    apiToken: string | null;
    setAuthToken(token: string | null): void;
    currentStudyId: ObjectId | null;
    lastPermissions: Permission[];
}

export const usePersistentState = create<PersistentState>()(
    persist(
        (set, get) => ({
            apiToken: null,
            devApiUrl: "http://localhost:8000/",
            getApiUrl: () => import.meta.env.PROD ? window.location.origin + "/" : get().devApiUrl,
            getFrontendBaseUrl: () =>  {
                let base_url = import.meta.env.BASE_URL || "/";
                return base_url.endsWith("/") ? base_url : base_url + "/";
            },
            apiTimeout: 6000,
            setAuthToken: (token) => {
                return set(() => ({apiToken: token}))
            },
            currentStudyId: null,
            lastPermissions: [
                {
                    role: Role.Participant,
                    type: PermissionType.Edit
                },
                {
                    role: Role.Nurse,
                    type: PermissionType.Edit
                },
            ]

        }),
        {
            name: 'local-storage',
        }
    )
);

