# GitHub Actions CI/CD 示例

项目使用 `.github/workflows/ci-cd.yml` 同时验证 Java 后端和 React 前端。

## 触发规则

- Pull Request 到 `main`：执行后端测试、前端 lint 和前端构建。
- Push 到 `main`：CI 通过后，发布后端和前端 Docker 镜像。
- `workflow_dispatch`：允许在 GitHub Actions 页面手动执行；只有从 `main` 执行时才发布镜像。

## CI 环境

后端 CI 使用 Java 21，并启动 MySQL 8.4 和 RabbitMQ 4.1 服务。执行测试前会加载
`src/sql/schema.sql`，随后运行：

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

前端 CI 使用 Node.js 24 和 `package-lock.json`，依次运行：

```bash
npm ci
npm run lint
npm run build
```

CI 生成的后端 JAR 和前端 `dist` 会作为 Actions Artifact 保留 7 天。

## Continuous Delivery

当代码进入 `main` 后，工作流会将两个镜像推送到 GitHub Container Registry：

```text
ghcr.io/<owner>/<repository>-backend:latest
ghcr.io/<owner>/<repository>-frontend:latest
```

每个镜像还会生成一个基于 Git 提交 SHA 的不可变标签。发布使用仓库自动提供的
`GITHUB_TOKEN`，不需要保存个人密码，但仓库的 Actions 设置必须允许工作流写入 Packages。

当前示例属于 Continuous Delivery：产物和镜像已经可部署，但不会自动连接服务器。
确定目标环境后，可以再添加需要人工审批的 `deploy` job，并将服务器或云平台凭据放入
GitHub Environment Secrets。

## 本地验证

```bash
mvn clean verify

cd frontend
npm ci
npm run lint
npm run build
```

构建容器镜像：

```bash
docker build -t transaction-monitoring-backend .
docker build -t transaction-monitoring-frontend ./frontend
```
