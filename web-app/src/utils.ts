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

import {LocalizedStr} from "./models/main.ts";

// Utility type for making specific properties optional, see https://stackoverflow.com/a/54178819/5106474
export type PartialBy<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>


export async function delay(time: number) {
    return new Promise(resolve => setTimeout(resolve, time));
}

/**
 * Returns the current locale.
 */
export const getLocale = () => {
    if (navigator.languages != undefined)
        return navigator.languages[0];
    return navigator.language;
}

/**
 * Converts a LocalizedStr to the representative string in the current locale.
 * @param str
 */
export const locStr = (str: LocalizedStr | null | undefined) => {
    if (str === null || str === undefined)
        return "";

    if (typeof str === "string") {
        return str;
    }

    const loc = getLocale().split("_")[0];

    if (loc in str)
        return str[loc];

    if ("en" in str)
        return str["en"];

    return str[Object.keys(str)[0]];
}

export function capitalizeFirstLetter(str: string) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}

export async function sleep(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

export function isValidEmailAddress(email: string) {
    if (!email)
        return false;

    // https://stackoverflow.com/a/46181/5106474
    return /^(([^<>()[\]\\.,;:\s@\"]+(\.[^<>()[\]\\.,;:\s@\"]+)*)|(\".+\"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/.test(email)
}

export function toIsoString(date: Date) {
    const tzo = -date.getTimezoneOffset(),
        dif = tzo >= 0 ? '+' : '-',
        pad = function (num: number) {
            return (num < 10 ? '0' : '') + num;
        };

    return date.getFullYear() +
        '-' + pad(date.getMonth() + 1) +
        '-' + pad(date.getDate()) +
        'T' + pad(date.getHours()) +
        ':' + pad(date.getMinutes()) +
        ':' + pad(date.getSeconds()) +
        dif + pad(Math.floor(Math.abs(tzo) / 60)) +
        ':' + pad(Math.abs(tzo) % 60);
}

export function toTimeStr(timestamp?: number) {
    if (!timestamp)
        return "n/a";
    const time = new Date(timestamp);

    return time.toLocaleString();
}

export function toggleListContainingElement<T>(list: T[], element: T) {
    const index = list.indexOf(element);
    if (index === -1) {
        list.push(element);
    } else {
        list.splice(index, 1);
    }
}
