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

import {Button, FormControl, InputLabel, MenuItem, Select, Tab, Tabs} from "@mui/material";
import {Link, Navigate, Outlet, Router, useMatchRoute, useRouterState} from "@tanstack/react-router";
import {
    foodEnumsRoute,
    foodImagesRoute,
    foodItemsRoute,
    indexRoute, mainRoutes, RoleSpecificRoute, studyDataOverviewRoute,
    inputFormsRoute, inputFieldsRoute,
    studyManagementRoute, unsupportedRoleRoute, licenseInfoRoute
} from "../routers/MainRouter.tsx";
import {useSessionState} from "../hooks/useState/useSessionState.ts";

function useCurrentId(): any {
    const routerState = useRouterState();
    routerState.matchIds
    for (const route of mainRoutes) {
        if (routerState.matchIds.includes(route.id)) {
            return route.id;
        }
    }
    return indexRoute.id;
}


export function RootContentView() {
    const currentUser = useSessionState(state => state.user);

    if (!currentUser)
        // This root view is only displayed to logged in users
        return null;

    const currentId = useCurrentId();

    const availableIds = RoleSpecificRoute[currentUser.role].routes.map(route => route.id);
    const ignoreTabIds = [licenseInfoRoute.id]
    const initRoute = RoleSpecificRoute[currentUser.role].initRoute;


    if (currentId === indexRoute.id || !(availableIds.includes(currentId)))
        // @ts-ignore
        return <Navigate to={initRoute.path} from={indexRoute.path} params={{}} search={{}}/>; // index route is just a redirect

    const contentLabels: { [key: string]: string } = {
        [studyManagementRoute.id]: "Study Management",
        [foodImagesRoute.id]: "Image Pool",
        [inputFormsRoute.id]: "Question Forms",
        [inputFieldsRoute.id]: "Form Fields",
        [foodEnumsRoute.id]: "Food Screens",
        [foodItemsRoute.id]: "Food Items",
        [studyDataOverviewRoute.id]: "Study Data",
        [unsupportedRoleRoute.id]: "Unsupported Role",
    }


    return (
        <>
            {!ignoreTabIds.includes(currentId) &&
                <div className={"nav-bar"}>
                    <Tabs
                        value={currentId}
                        textColor="primary"
                        indicatorColor="secondary"
                        variant="scrollable"
                    >
                        {
                            availableIds.filter(id => !ignoreTabIds.includes(id)).map(tabValue => (
                                // @ts-ignore
                                <Tab
                                    value={tabValue}
                                    key={tabValue}
                                    label={contentLabels[tabValue] ?? tabValue}

                                    component={Link}
                                    from={indexRoute.path}
                                    to={tabValue}
                                    params={{}}
                                    search={{}}
                                />))
                        }
                    </Tabs>
                </div>
            }

            <main className="tab-content">
                <Outlet/>
            </main>

            <footer className={"footer"}>
                <p>Copyright (c) 2023-2024<br/>Technical University of Applied Sciences Wildau<br/><a
                    href="https://en.th-wildau.de/research-transfer/research/telematics/" target={"_blank"}>Telematics
                    Research Group</a></p>
                <p><Link to={licenseInfoRoute.id} search={{}} params={{}}>License Info</Link></p>
            </footer>
        </>

    )
}