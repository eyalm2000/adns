// IPrivilegedService.aidl
package com.eyalm.addns;

// Declare any non-default types here with import statements

interface IPrivilegedService {
    boolean grantWriteSecureSettings(String packageName);
}