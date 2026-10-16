FROM searxng/searxng:latest

USER root

# L'image officielle déclare /etc/searxng comme VOLUME : à chaque redéploiement,
# docker compose réattache l'ancien volume, dont le settings.yml masquerait celui
# de l'image. Notre configuration vit donc hors de ce volume, dans /opt/tribbae.
COPY settings.yml /opt/tribbae/searxng/settings.yml
COPY limiter.toml /opt/tribbae/searxng/limiter.toml
RUN chown -R searxng:searxng /opt/tribbae/searxng && \
    chmod 644 /opt/tribbae/searxng/settings.yml /opt/tribbae/searxng/limiter.toml

ENV SEARXNG_SETTINGS_PATH=/opt/tribbae/searxng/settings.yml
ENV SEARXNG_LIMITER_CFG=/opt/tribbae/searxng/limiter.toml

USER searxng

EXPOSE 8080
