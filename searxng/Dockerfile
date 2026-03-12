FROM searxng/searxng:latest

USER root

# Copier les fichiers de configuration
COPY settings.yml /etc/searxng/settings.yml
COPY limiter.toml /etc/searxng/limiter.toml

# S'assurer que les permissions sont correctes
RUN chown -R searxng:searxng /etc/searxng && \
    chmod 644 /etc/searxng/settings.yml && \
    chmod 644 /etc/searxng/limiter.toml && \
    ls -la /etc/searxng/ && \
    echo "Configuration files copied successfully"

# Variables d'environnement pour SearXNG
ENV SEARXNG_SETTINGS_PATH=/etc/searxng/settings.yml
ENV SEARXNG_LIMITER_CFG=/etc/searxng/limiter.toml

USER searxng

# Exposer le port
EXPOSE 8080

# Commande par défaut
CMD ["/usr/local/searxng/dockerfiles/docker-entrypoint.sh"]
