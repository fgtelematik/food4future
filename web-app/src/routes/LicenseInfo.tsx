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
import "../style/LicenseInfo.css";
import {Tab, Tabs} from "@mui/material";
import {usePersistentState} from "../hooks/useState/usePersistentState.ts";

enum LicenseCategory {
    MainInfo = "License Info",
    ThirdPartyFrontend = "Third Party Licenses (Frontend)",
    ThirdPartyBackend = "Third Party Licenses (Backend)"
}

const mainInfo = <>
    <b>f4f Study Management Portal (Web App)</b> and<br/>
    <b>f4f Study Companion Backend Server</b> and<br/>
    <b>f4f Study Companion Android App</b>
    {`
    
    Copyright (c) 2024 Technical University of Applied Sciences Wildau
    
    Author: Philipp Wagner, Research Group Telematics
    Contact: fgtelematik@th-wildau.de
    
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation version 2.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.`}
</>

export const LicenseInfo: FC = () => {
    const [currentCategory, setCurrentCategory] = useState<LicenseCategory>(LicenseCategory.MainInfo);
    const [frontendInfo, setFrontendInfo] = useState<string>("Loading frontend license info...");
    const [backendInfo, setBackendInfo] = useState<string>("Loading backend license info...");

    const getFrontendBaseUrl = usePersistentState().getFrontendBaseUrl;

    async function loadLicenseInfos() {
        try {
            const url = getFrontendBaseUrl() + "licenses-disclaimer.txt" ;
            console.log(url);
            const response = await fetch(url );
            const text = await response.text();
            setFrontendInfo(text);
        } catch (e) {
            setFrontendInfo("Failed to load frontend license info.");
        }

        if(!import.meta.env.PROD) {
            setBackendInfo("No backend license info available in development mode.");
            return;
        }

        try {
            const response = await fetch("/license_info");
            const text = await response.text();
            setBackendInfo(text);
        } catch (e) {
            setBackendInfo("Failed to load backend license info.");
        }
    }

    useEffect(() => {
        loadLicenseInfos();
    }, []);
    return (
        <div className={"license-info"}>
            <h2>License Info</h2>
            <Tabs
                value={currentCategory}
                onChange={(_, newValue) => setCurrentCategory(newValue)}
                textColor="primary"
                indicatorColor="secondary"
                variant="scrollable"
            >
                {
                    Object.values(LicenseCategory).map(tabValue => (
                        <Tab
                            value={tabValue}
                            key={tabValue}
                            label={tabValue}
                        />))
                }
            </Tabs>

            <p className={"license-text"}>
                {currentCategory === LicenseCategory.MainInfo ? mainInfo : currentCategory === LicenseCategory.ThirdPartyFrontend ? frontendInfo : backendInfo}
            </p>
        </div>
    );
};