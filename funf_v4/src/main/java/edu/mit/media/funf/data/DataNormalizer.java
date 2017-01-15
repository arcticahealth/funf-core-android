/**
 * 
 * Funf: Open Sensing Framework
 * Copyright (C) 2010-2011 Nadav Aharony, Wei Pan, Alex Pentland.
 * Acknowledgments: Alan Gardner
 * Contact: nadav@media.mit.edu
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with Funf. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package edu.mit.media.funf.data;

import android.util.Log;

import org.jetbrains.annotations.Nullable;

public interface DataNormalizer<T> {

	public T normalize(T data);
	
	public class EmailNormalizer implements DataNormalizer<String> {

		@Override
		public String normalize(String data) {
			return data == null ? null : data.trim().toLowerCase();
		}
		
	}
	
	public class PhoneNumberNormalizer implements DataNormalizer<String> {

		private static final String TAG = PhoneNumberNormalizer.class.getSimpleName();

		@Override
		public String normalize(@Nullable String numberString) {
			if (numberString == null) {
				Log.e(TAG,"Request to normalize a null string, returning an empty string");
				return "";
			}
			numberString = numberString.replaceAll("[^0-9]","");
			int i = numberString.length();
			if (i <= 10)
				return numberString;
			else
				return numberString.substring(i - 10);
		}
		
	}
}
