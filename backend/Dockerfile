FROM node:22-alpine

WORKDIR /app
COPY package.json ./
COPY src ./src

ENV NODE_ENV=production
ENV PORT=8787
ENV DATA_DIR=/data

EXPOSE 8787
VOLUME ["/data"]

CMD ["node", "src/index.mjs"]
