# Alertmanager 飞书中转服务
# 接收 Alertmanager 的 webhook，转换为飞书消息格式后发送
# 同时支持自定义 Agent 接收（预留扩展）

import json
import requests
from flask import Flask, request, jsonify

app = Flask(__name__)

# ⚠️ 替换为你的飞书机器人 Webhook URL
# 获取方式：飞书群 → 设置 → 群机器人 → 添加机器人 → 复制 Webhook 地址
FEISHU_WEBHOOK = 'https://open.feishu.cn/open-apis/bot/v2/hook/YOUR_WEBHOOK_TOKEN'

# Agent 回调 URL（可选，用于自定义处理）
AGENT_WEBHOOK = 'http://localhost:5002/agent-alert'


@app.route('/alert', methods=['POST'])
def handle_alert():
    """接收 Alertmanager 的 webhook"""
    data = request.json
    
    if not data or 'alerts' not in data:
        return jsonify({'error': 'Invalid alert data'}), 400
    
    alerts = data.get('alerts', [])
    
    # 1. 发送到飞书
    feishu_result = send_to_feishu(alerts)
    
    # 2. 发送到 Agent（可选）
    agent_result = send_to_agent(data)
    
    return jsonify({
        'feishu': feishu_result,
        'agent': agent_result,
        'total_alerts': len(alerts)
    }), 200


def send_to_feishu(alerts):
    """转换告警格式并发送到飞书"""
    messages = []
    
    for alert in alerts:
        status = alert.get('status', 'unknown')
        labels = alert.get('labels', {})
        annotations = alert.get('annotations', {})
        
        alert_name = labels.get('alertname', 'Unknown')
        severity = labels.get('severity', 'unknown')
        instance = labels.get('instance', 'unknown')
        summary = annotations.get('summary', 'No summary')
        description = annotations.get('description', 'No description')
        
        # 状态图标
        icon = '🔴' if status == 'firing' else '🟢'
        status_text = '触发' if status == 'firing' else '恢复'
        
        messages.append(
            f"{icon} **{alert_name}** [{severity.upper()}] {status_text}\n"
            f"实例：{instance}\n"
            f"摘要：{summary}\n"
            f"详情：{description}"
        )
    
    # 飞书交互式卡片格式
    feishu_payload = {
        "msg_type": "interactive",
        "card": {
            "header": {
                "title": {
                    "tag": "plain_text",
                    "content": f"Prometheus 告警 ({len(messages)}条)"
                },
                "template": "red" if any(a.get('status') == 'firing' for a in alerts) else "green"
            },
            "elements": [
                {
                    "tag": "markdown",
                    "content": "\n\n".join(messages)
                }
            ]
        }
    }
    
    try:
        response = requests.post(FEISHU_WEBHOOK, json=feishu_payload, timeout=5)
        if response.status_code == 200:
            return {'success': True, 'status_code': 200}
        else:
            return {'success': False, 'error': response.text}
    except Exception as e:
        return {'success': False, 'error': str(e)}


def send_to_agent(data):
    """发送到 Agent（预留扩展）"""
    if not AGENT_WEBHOOK:
        return {'success': False, 'error': 'Agent webhook not configured'}
    
    try:
        response = requests.post(AGENT_WEBHOOK, json=data, timeout=5)
        if response.status_code == 200:
            return {'success': True, 'status_code': 200}
        else:
            return {'success': False, 'error': response.text}
    except Exception as e:
        return {'success': False, 'error': str(e)}


@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({'status': 'ok'}), 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=False)
