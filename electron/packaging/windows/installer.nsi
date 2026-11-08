Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "WinVer.nsh"

Name "LexAid"
OutFile "${OUTFILE}"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\LexAid"
SetCompressor /SOLID lzma
SetCompressorDictSize 16
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "LexAid"
VIAddVersionKey "FileDescription" "LexAid Installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "LexAid"

!define MUI_ICON "${ICONFILE}"
!define MUI_UNICON "${ICONFILE}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\lexaid-app\LexAid.exe"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\LexAid"

!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

LangString Unsupported ${LANG_SIMPCHINESE} "LexAid 需要 64 位 Windows 10 或更高版本。"
LangString Unsupported ${LANG_ENGLISH} "LexAid requires 64-bit Windows 10 or later."
LangString AppInUse ${LANG_SIMPCHINESE} "请退出 LexAid 后重试。"
LangString AppInUse ${LANG_ENGLISH} "Quit LexAid and try again."
LangString InstallFailure ${LANG_SIMPCHINESE} "无法写入安装目录。请检查磁盘空间和目录权限。"
LangString InstallFailure ${LANG_ENGLISH} "Cannot update the installation directory. Check free disk space and access permissions."

LangString Busy ${LANG_SIMPCHINESE} "已有一个 LexAid 安装或卸载程序在运行。"
LangString Busy ${LANG_ENGLISH} "Another LexAid installer or uninstaller is running."

!macro LockInstaller
  System::Call 'kernel32::CreateMutexW(p 0, i 0, w "Local\LexAidDesktopInstaller")p.r1 ?e'
  Pop $0
  ${If} $1 == 0
  ${OrIf} $0 == 183
    MessageBox MB_ICONSTOP|MB_OK "$(Busy)" /SD IDOK
    SetErrorLevel 1
    Abort
  ${EndIf}
!macroend

Function .onInit
  !insertmacro LockInstaller
  SetShellVarContext current
  SetRegView 64
  StrCpy $INSTDIR "$LOCALAPPDATA\Programs\LexAid"
  ${IfNot} ${RunningX64}
  ${OrIfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP|MB_OK "$(Unsupported)" /SD IDOK
    Abort
  ${EndIf}
FunctionEnd

Function un.onInit
  !insertmacro LockInstaller
  SetShellVarContext current
  SetRegView 64
FunctionEnd

; Windows prevents opening a running executable for exclusive write access.
!macro CheckClosed
  ${If} ${FileExists} "$INSTDIR\lexaid-app\LexAid.exe"
    System::Call 'kernel32::CreateFileW(w "$INSTDIR\lexaid-app\LexAid.exe", i 0x40000000, i 0, p 0, i 3, i 0, p 0)p.r0'
    ${If} $0 == -1
      MessageBox MB_ICONSTOP|MB_OK "$(AppInUse)" /SD IDOK
      SetErrorLevel 1
      Abort
    ${EndIf}
    System::Call 'kernel32::CloseHandle(p r0)'
  ${EndIf}
!macroend

Section "LexAid"
  !insertmacro CheckClosed
  ; Restore an interrupted upgrade before staging another payload.
  IfFileExists "$INSTDIR\lexaid-app\LexAid.exe" active
  IfFileExists "$INSTDIR\lexaid-previous\LexAid.exe" 0 active
  Rename "$INSTDIR\lexaid-previous" "$INSTDIR\lexaid-app"
  IfErrors failed
active:
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\lexaid-next"
  SetOutPath "$INSTDIR\lexaid-next"
  ClearErrors
  File /r "${PAYLOAD}/*"
  IfErrors failed
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\lexaid-previous"
  IfFileExists "$INSTDIR\lexaid-app\LexAid.exe" 0 activate
  ClearErrors
  Rename "$INSTDIR\lexaid-app" "$INSTDIR\lexaid-previous"
  IfErrors failed
activate:
  ClearErrors
  Rename "$INSTDIR\lexaid-next" "$INSTDIR\lexaid-app"
  IfErrors restore
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\LexAid"
  CreateShortcut "$SMPROGRAMS\LexAid\LexAid.lnk" "$INSTDIR\lexaid-app\LexAid.exe"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "LexAid"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "LexAid"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\lexaid-app\LexAid.exe"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
  RMDir /r "$INSTDIR\lexaid-previous"
  Goto finished
restore:
  Rename "$INSTDIR\lexaid-previous" "$INSTDIR\lexaid-app"
failed:
  MessageBox MB_ICONSTOP|MB_OK "$(InstallFailure)" /SD IDOK
  SetErrorLevel 1
  Abort
finished:
SectionEnd

Section "Uninstall"
  !insertmacro CheckClosed
  Delete "$SMPROGRAMS\LexAid\LexAid.lnk"
  RMDir "$SMPROGRAMS\LexAid"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
  RMDir /r "$INSTDIR\lexaid-app"
  RMDir /r "$INSTDIR\lexaid-next"
  RMDir /r "$INSTDIR\lexaid-previous"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
SectionEnd
