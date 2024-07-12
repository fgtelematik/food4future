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

import {AnyRoute, RootRoute, Route, Router} from "@tanstack/react-router";
import {RootView} from "../routes/Root.tsx";
import {FoodImagesView} from "../routes/FoodImages.tsx";
import {FoodItemsView} from "../routes/FoodItems.tsx";
import {FoodEnumsView} from "../routes/FoodEnums.tsx";
import {NotFoundContent} from "../routes/NotFoundContent.tsx";
import {StudyManagementView} from "../routes/StudyManagement.tsx";
import {RootContentView} from "../routes/RootContent.tsx";
import {StudyDataOverview} from "../routes/StudyDataOverview.tsx";
import {UnsupportedRole} from "../routes/UnsupportedRole.tsx";
import {Role} from "../models/auth.ts";
import {InputFormsView} from "../routes/InputForms.tsx";
import {InputFieldsView} from "../routes/InputFields.tsx";
import {LicenseInfo} from "../routes/LicenseInfo.tsx";

export const rootRoute = new RootRoute({
    component: RootView,
})

export const studyManagementRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/studies",
});

export const studyManagementRootRoute = new Route({
    getParentRoute: () => studyManagementRoute,
    path: "/",
    component: StudyManagementView
});

const studyManagementRoutes = [studyManagementRootRoute];
studyManagementRoute.addChildren(studyManagementRoutes);

export const foodImagesRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/food-images",
    component: FoodImagesView
});


export const foodItemsRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/food-items",
    component: FoodItemsView
});

export const foodEnumsRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/food-screens",
    component: FoodEnumsView,
});

export const inputFormsRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/forms",
    component: InputFormsView
});

export const inputFieldsRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/fields",
    component: InputFieldsView
});


export const studyDataOverviewRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/data",
    component: StudyDataOverview
});

export const licenseInfoRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/licenses",
    component: LicenseInfo
});


export const unsupportedRoleRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/unsupported-role",
    component: UnsupportedRole
});

export const indexRoute = new Route({
    getParentRoute: () => rootRoute,
    path: import.meta.env.BASE_URL ?? "/",
    component: () => <RootContentView/>
});

const notFoundRoute = new Route({
    getParentRoute: () => indexRoute,
    path: "/*",
    component: NotFoundContent
});

export const mainRoutes = [
    studyManagementRoute,
    foodImagesRoute,
    foodItemsRoute,
    foodEnumsRoute,
    inputFieldsRoute,
    inputFormsRoute,
    studyDataOverviewRoute,
    licenseInfoRoute,
    unsupportedRoleRoute
];

const routeTree = rootRoute.addChildren([indexRoute, notFoundRoute, ...mainRoutes]);

export const mainRouter = new Router({routeTree});

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof mainRouter
    }
}



export const RoleSpecificRoute :
    { [key in Role]: { routes: AnyRoute[], initRoute: AnyRoute } }
    = {
    [Role.Administrator]: {
        routes : [
            studyManagementRoute,
            foodImagesRoute,
            foodItemsRoute,
            foodEnumsRoute,
            inputFormsRoute,
            inputFieldsRoute,
            licenseInfoRoute,
        ],
        initRoute: studyManagementRoute
    },
    [Role.Supervisor]: {
        routes : [
            studyDataOverviewRoute,
            licenseInfoRoute,
        ],
        initRoute: studyDataOverviewRoute
    },
    [Role.Nurse]: {
        routes : [
            unsupportedRoleRoute,
            licenseInfoRoute,
        ],
        initRoute: unsupportedRoleRoute
    },
    [Role.Participant]: {
        routes : [
            unsupportedRoleRoute,
            licenseInfoRoute,
        ],
        initRoute: unsupportedRoleRoute
    },
}


