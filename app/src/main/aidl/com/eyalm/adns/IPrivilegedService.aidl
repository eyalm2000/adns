// IPrivilegedService.aidl
package com.eyalm.adns;

// Declare any non-default types here with import statements

interface IPrivilegedService {
    boolean grantWriteSecureSettings(String packageName);
}