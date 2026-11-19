SKIPUNZIP=0
ui_print "- Omalauncher+ as a system app"
ui_print "- Grants: CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS"
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
ui_print "- Soft-reboot to apply."
