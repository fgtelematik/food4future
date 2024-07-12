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

import {createTheme, ThemeOptions} from "@mui/material";

export const muiThemeOptions : ThemeOptions = {
    palette: {
        primary: {
            main: "#58afcc",
        },
        warning: {
            main: "#b54500",
        },
        success: {
            main: "#00b57f",
        },
        secondary: {
            main: '#AAD8D3',
        },
    },
};
export const mainTheme = createTheme(muiThemeOptions);
export const darkTheme = createTheme({...muiThemeOptions, palette: {...muiThemeOptions.palette, mode: "dark"}});

