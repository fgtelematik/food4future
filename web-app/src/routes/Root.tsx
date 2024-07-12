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

import {ThemeProvider, Typography} from "@mui/material";
import '../style/root.css';
import {mainTheme} from "../theme.ts";
import logo from "../assets/logo_full.png";
import {UserMenu} from "../components/UserMenu.tsx";
import {useEffect} from "react";
import {LoginForm} from "../components/LoginForm.tsx";
import {useSessionState} from "../hooks/useState/useSessionState.ts";
import {usePersistentState} from "../hooks/useState/usePersistentState.ts";
import useApi from "../hooks/useApi";
import AlertDialog from "../components/AlertDialog.tsx";
import {LoadingIndicator} from "../components/LoadingIndicator.tsx";
import {RootContentView} from "./RootContent.tsx";
import {Link, Outlet} from "@tanstack/react-router";
import {RoleSpecificRoute, rootRoute, studyManagementRoute} from "../routers/MainRouter.tsx";


export function RootView() {

    const currentUser = useSessionState(state => state.user);
    const authToken = usePersistentState(state => state.apiToken);
    const globalError = useSessionState(state => state.globalError);
    const alertDialogProps = useSessionState(state => state.alertDialog);

    const api = useApi();

    useEffect(() => {

        if (!!authToken)
            api.fetchCurrentUser();

        useSessionState.setState({globalError: null});

    }, []);

    return (
        <ThemeProvider theme={mainTheme}>
            <div className="root">
                <LoadingIndicator/>
                <div className="top-bar">

                    <div className="title">
                        <Link to={!!currentUser?.role ? RoleSpecificRoute[currentUser?.role].initRoute.id : undefined} search={{}} params={{}} >
                            <img className="logo" src={logo} alt={"Logo"}/>
                        </Link>
                        <Typography variant="h6" noWrap>
                            Study Management Portal
                        </Typography>
                    </div>

                    <UserMenu/>

                </div>

                <div className={"main-content"}>
                    {
                        globalError ?
                            <div className={"study-management"}>
                                <p className={"error"}>An unexpected error occurred during your last request.<br/><br/>Description:<br/>{globalError}</p>
                                <p>Please try reloading the page.</p>
                            </div>
                            :
                            (currentUser != null ?
                                <Outlet/>
                                :
                                <LoginForm/>)
                    }
                </div>

                {alertDialogProps && <AlertDialog {...alertDialogProps}/>}

            </div>

        </ThemeProvider>
    );
}