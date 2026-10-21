FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server.js ./
# La imagen oficial de node ya trae el usuario "node" sin privilegios
USER node
EXPOSE 3000
CMD ["node", "server.js"]
