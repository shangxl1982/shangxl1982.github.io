import { defineConfig } from 'vitepress'
import { MermaidMarkdown } from 'vitepress-plugin-mermaid'
import { katex as katexPlugin } from '@mdit/plugin-katex'

export default defineConfig({
  base: '/aitechs/',
  lang: 'en-US',
  title: 'AI Techs',
  description: '20+ Years of Engineering — From Storage Systems to AI Technologies',
  ignoreDeadLinks: true,
  appearance: true,

  head: [
    ['link', { rel: 'stylesheet', href: '/aitechs/katex.min.css' }],
  ],

  markdown: {
    config(md) {
      md.use(katexPlugin)
      md.use(MermaidMarkdown)
    },
  },

  themeConfig: {
    logo: '/logo.png',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Blog', link: '/blog/' },
      { text: 'Traditional', link: '/traditional/' },
      { text: 'AI', link: '/ai/' },
    ],

    sidebar: {
      '/blog/': [
        {
          text: 'Blog Posts',
          collapsed: false,
          items: [
            { text: 'Blog Index', link: '/blog/' },
            { text: 'First Post - Getting Started', link: '/blog/first-post' },
          ],
        },
      ],

      '/traditional/': [
        {
          text: 'Traditional Tech',
          items: [
            { text: 'Overview', link: '/traditional/' },
            {
              text: 'Distributed Systems',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/traditional/distributed-systems/' },
              ],
            },
            {
              text: 'Storage Engines',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/traditional/storage-engines/' },
              ],
            },
            {
              text: 'File System',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/traditional/filesystem/' },
              ],
            },
            {
              text: 'Big Data',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/traditional/big-data/' },
              ],
            },
            {
              text: 'Columnar Storage',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/traditional/columnar-storage/' },
              ],
            },
          ],
        },
      ],

      '/ai/': [
        {
          text: 'AI Tech',
          items: [
            { text: 'Overview', link: '/ai/' },
            {
              text: 'Model Architecture',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/model-architecture/' },
              ],
            },
            {
              text: 'Inference Optimization',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/inference-optimization/' },
              ],
            },
            {
              text: 'Attention Mechanism',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/attention-mechanism/' },
              ],
            },
            {
              text: 'Fine-tuning & Training',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/finetuning-training/' },
              ],
            },
            {
              text: 'Multi-Agent Systems',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/multi-agent/' },
              ],
            },
            {
              text: 'RAG Technologies',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/rag/' },
              ],
            },
            {
              text: 'Evaluation',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/evaluation/' },
              ],
            },
            {
              text: 'Agent Frameworks',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/agent-frameworks/' },
              ],
            },
            {
              text: 'Embedding & Classification',
              collapsed: true,
              items: [
                { text: 'Overview', link: '/ai/embedding/' },
              ],
            },
          ],
        },
      ],
    },

    search: {
      provider: 'local',
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/shangxiaole' },
    ],
  },
})
