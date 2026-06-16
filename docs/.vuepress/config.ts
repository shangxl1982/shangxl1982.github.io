import { viteBundler } from '@vuepress/bundler-vite'
import { defineUserConfig } from 'vuepress'
import { defaultTheme } from '@vuepress/theme-default'
import { markdownMathPlugin } from '@vuepress/plugin-markdown-math'
import { markdownChartPlugin } from '@vuepress/plugin-markdown-chart'
import { searchPlugin } from '@vuepress/plugin-search'

export default defineUserConfig({
  base: '/aitechs/',
  lang: 'en-US',
  title: 'AI Techs',
  description: 'AI Technologies Blog',

  bundler: viteBundler(),

  theme: defaultTheme({
    logo: '/logo.png',
    navbar: [
      {
        text: 'Home',
        link: '/',
      },
      {
        text: 'Blog',
        link: '/blog/',
      },
      {
        text: 'Traditional',
        link: '/traditional/',
      },
      {
        text: 'AI',
        link: '/ai/',
      },
    ],
    sidebar: {
      '/blog/': [
        {
          text: 'Blog Posts',
          collapsible: true,
          children: [
            '/blog/',
            '/blog/first-post',
          ],
        },
      ],
      '/traditional/': [
        {
          text: 'Traditional Tech',
          collapsible: false,
          children: [
            '/traditional/',
            {
              text: 'Distributed Systems',
              collapsible: true,
              children: ['/traditional/distributed-systems/'],
            },
            {
              text: 'Storage Engines',
              collapsible: true,
              children: ['/traditional/storage-engines/'],
            },
            {
              text: 'File System',
              collapsible: true,
              children: ['/traditional/filesystem/'],
            },
            {
              text: 'Big Data',
              collapsible: true,
              children: ['/traditional/big-data/'],
            },
            {
              text: 'Columnar Storage',
              collapsible: true,
              children: ['/traditional/columnar-storage/'],
            },
          ],
        },
      ],
      '/ai/': [
        {
          text: 'AI Tech',
          collapsible: false,
          children: [
            '/ai/',
            {
              text: 'Model Architecture',
              collapsible: true,
              children: ['/ai/model-architecture/'],
            },
            {
              text: 'Inference Optimization',
              collapsible: true,
              children: ['/ai/inference-optimization/'],
            },
            {
              text: 'Attention Mechanism',
              collapsible: true,
              children: ['/ai/attention-mechanism/'],
            },
            {
              text: 'Fine-tuning & Training',
              collapsible: true,
              children: ['/ai/finetuning-training/'],
            },
            {
              text: 'Multi-Agent Systems',
              collapsible: true,
              children: ['/ai/multi-agent/'],
            },
            {
              text: 'RAG Technologies',
              collapsible: true,
              children: ['/ai/rag/'],
            },
            {
              text: 'Evaluation',
              collapsible: true,
              children: ['/ai/evaluation/'],
            },
            {
              text: 'Agent Frameworks',
              collapsible: true,
              children: ['/ai/agent-frameworks/'],
            },
            {
              text: 'Embedding & Classification',
              collapsible: true,
              children: ['/ai/embedding/'],
            },
          ],
        },
      ],
    },
  }),

  plugins: [
    // KaTeX support
    markdownMathPlugin({
      type: 'katex',
    }),
    // Mermaid support
    markdownChartPlugin({
      mermaid: true,
    }),
    // Search support
    searchPlugin({
      // allow searching all pages
      isSearchable: () => true,
      maxSuggestions: 10,
    }),
  ],
})