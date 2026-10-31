# Development image: runs the Vite dev server. Source is bind-mounted by compose.yaml.
FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
EXPOSE 5173
CMD ["npm", "run", "dev"]
