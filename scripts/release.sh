#!/bin/bash
set -e

# Script pour créer une nouvelle release Tribbae
# Usage: ./scripts/release.sh 1.0.0

if [ -z "$1" ]; then
  echo "Usage: $0 <version>"
  echo "Example: $0 1.0.0"
  exit 1
fi

VERSION=$1
TAG="v${VERSION}"

echo "🚀 Création de la release ${TAG}"

# Vérifier que le repo est propre
if [ -n "$(git status --porcelain)" ]; then
  echo "❌ Le repository contient des modifications non commitées"
  git status --short
  exit 1
fi

# Vérifier qu'on est sur main
BRANCH=$(git branch --show-current)
if [ "$BRANCH" != "main" ]; then
  echo "⚠️  Vous n'êtes pas sur la branche main (actuellement sur ${BRANCH})"
  read -p "Continuer quand même ? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

# Vérifier que le tag n'existe pas déjà
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "❌ Le tag ${TAG} existe déjà"
  exit 1
fi

# Créer le tag
echo "📝 Création du tag ${TAG}"
git tag -a "$TAG" -m "Release ${VERSION}"

# Pousser le tag
echo "⬆️  Push du tag vers GitHub"
git push origin "$TAG"

echo "✅ Release ${TAG} créée avec succès !"
echo ""
echo "📦 Les GitHub Actions vont maintenant :"
echo "   1. Builder l'APK Android"
echo "   2. Créer la release GitHub avec l'APK"
echo "   3. Builder et pousser les images Docker"
echo ""
echo "🔗 Suivez la progression sur : https://github.com/$(git config --get remote.origin.url | sed 's/.*github.com[:/]\(.*\)\.git/\1/')/actions"
