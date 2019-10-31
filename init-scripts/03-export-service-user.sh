#!/usr/bin/env bash

if test -f /secrets/serviceuser/srvdp-jrnf-ferdig/username;
then
    export  SRVDAGPENGER_JOURNALFORING_FERDIGSTILL_USERNAME=$(cat /secrets/serviceuser/srvdp-jrnf-ferdig/username)
fi
if test -f /secrets/serviceuser/srvdp-jrnf-ferdig/password;
then
    export  SRVDAGPENGER_JOURNALFORING_FERDIGSTILL_PASSWORD=$(cat /secrets/serviceuser/srvdp-jrnf-ferdig/password)
fi


