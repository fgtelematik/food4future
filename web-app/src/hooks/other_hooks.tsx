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

import {useDataState} from "./useState/useData.ts";
import {FieldType, fieldTypeLabels, InputField} from "../models/form.ts";


export function useGetFullInputFieldTypeLabel(item?: undefined): (item: InputField) => string;
export function useGetFullInputFieldTypeLabel(item?: InputField): string
export function useGetFullInputFieldTypeLabel(item?: InputField): string | ((item: InputField) => string) {
    const allSubforms = useDataState(state => state.inputForms);
    const allEnums = useDataState(state => state.inputEnums);

    if (item)
        return useGetFullInputFieldTypeLabel()(item);

    return (item: InputField) => {
        const subformLabel = (!!item.adt_enum_id && allSubforms.find(subform => subform.id === item.adt_enum_id)?.identifier) || undefined;
        const enumLabel = (!!item.adt_enum_id && allEnums.find(enumItem => enumItem.id === item.adt_enum_id)?.identifier) || undefined;

        let fieldTypeLabel = fieldTypeLabels[item.datatype];
        let subtypeLabel: string | undefined = undefined;

        if (item.datatype === FieldType.FormType && !!subformLabel)
            subtypeLabel = subformLabel;

        if (item.datatype === FieldType.EnumType && !!enumLabel)
            subtypeLabel = enumLabel;

        if (item.datatype === FieldType.ListType && !!item.elements_type) {
            subtypeLabel = fieldTypeLabels[item.elements_type];

            if (item.elements_type === FieldType.FormType && !!subformLabel)
                subtypeLabel += ": " + subformLabel;

            if (item.elements_type === FieldType.EnumType && !!enumLabel)
                subtypeLabel += ": " + enumLabel;
        }

        if (subtypeLabel)
            fieldTypeLabel += " (" + subtypeLabel + ")";

        return fieldTypeLabel;
    }
}