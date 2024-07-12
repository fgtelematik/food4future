#!/bin/sh
echo "Preparing custom templates for f4f nginx instance...";

mkdir -p /etc/nginx/templates

# Copy template depending on HTTPS configuration
if [ "$NGINX_ENABLE_HTTPS" = "true" ]
then
    echo "HTTPS is enabled."
    cp -a /templates/https.conf.template /etc/nginx/templates/default.conf.template
else
    echo "HTTPS is disabled."
    cp -a /templates/http.conf.template /etc/nginx/templates/default.conf.template
fi

# Start original entrypoint
/bin/sh /docker-entrypoint.sh nginx -g "daemon off;"